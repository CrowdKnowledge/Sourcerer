/* 
 * Sourcerer: an infrastructure for large-scale source code analysis.
 * Copyright (C) by contributors. See CONTRIBUTORS.txt for full list.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package edu.uci.ics.sourcerer.tools.java.db.resolver;

import static edu.uci.ics.sourcerer.util.io.Logging.logger;

import java.util.Collection;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import edu.uci.ics.sourcerer.tools.java.db.importer.TypeUtils;
import edu.uci.ics.sourcerer.tools.java.db.schema.EntitiesTable;
import edu.uci.ics.sourcerer.tools.java.db.schema.RelationsTable;
import edu.uci.ics.sourcerer.tools.java.model.types.Entity;
import edu.uci.ics.sourcerer.tools.java.model.types.Relation;
import edu.uci.ics.sourcerer.tools.java.model.types.RelationClass;
import edu.uci.ics.sourcerer.util.Pair;
import edu.uci.ics.sourcerer.util.io.TaskProgressLogger;
import edu.uci.ics.sourcerer.utils.db.QueryExecutor;
import edu.uci.ics.sourcerer.utils.db.sql.SelectQuery;
import edu.uci.ics.sourcerer.utils.db.sql.TypedQueryResult;

/**
 * @author Joel Ossher (jossher@uci.edu)
 */
public class ProjectTypeModel {
  private Map<String, ModeledEntity> entities;
  private Map<Integer, ModeledEntity> reverseMap;
  private LibraryTypeModel libraryModel;
  
  private UnknownEntityCache unknowns;
  
  private QueryExecutor exec;
  private Integer projectID;
  
  private ProjectTypeModel(QueryExecutor exec, Integer projectID, LibraryTypeModel libraryModel, UnknownEntityCache unknowns) {
    this.exec = exec;
    this.projectID = projectID;
    this.libraryModel = libraryModel;
    this.unknowns = unknowns;
    this.entities = new HashMap<>();
  }
  
  private void add(String fqn, ModeledEntity entity) {
    if (entities.containsKey(fqn)) {
      logger.severe("Duplicate FQN: " + fqn);
    } else {
      entities.put(fqn, entity);
    }
  }
  
  public void add(String fqn, Integer entityID) {
    add(fqn, new ModeledEntity(fqn, null, entityID, RelationClass.INTERNAL));
  }

  private void loadEntities(TaskProgressLogger task) {
    task.start("Loading library entities", "entities loaded");
    try (SelectQuery query = exec.makeSelectQuery(EntitiesTable.TABLE)) {
      query.addSelects(EntitiesTable.ENTITY_ID, EntitiesTable.FQN, EntitiesTable.ENTITY_TYPE, EntitiesTable.PARAMS, EntitiesTable.RAW_PARAMS);
      query.andWhere(EntitiesTable.PROJECT_ID.compareEquals(projectID), EntitiesTable.ENTITY_TYPE.compareIn(EnumSet.of(Entity.CLASS, Entity.INTERFACE, Entity.ENUM, Entity.ANNOTATION, Entity.CONSTRUCTOR, Entity.METHOD, Entity.ANNOTATION_ELEMENT, Entity.ENUM_CONSTANT, Entity.FIELD, Entity.PACKAGE, Entity.INITIALIZER)));

      TypedQueryResult result = query.selectStreamed();
      while (result.next()) {
        Integer entityID = result.getResult(EntitiesTable.ENTITY_ID);
        String fqn = result.getResult(EntitiesTable.FQN);
        Entity type = result.getResult(EntitiesTable.ENTITY_TYPE);
        String params = result.getResult(EntitiesTable.PARAMS);
        ModeledEntity entity = new ModeledEntity(fqn, type, entityID, RelationClass.INTERNAL);
        if (params == null) {
          add(fqn, entity);
        } else {
          String rawParams = result.getResult(EntitiesTable.RAW_PARAMS);
          if (rawParams != null) {
            add(fqn + rawParams, entity);
          }
          add(fqn + params, entity);
        }
        if (reverseMap != null && (type == Entity.CLASS || type == Entity.ENUM || type == Entity.ANNOTATION || type == Entity.INTERFACE)) {
          reverseMap.put(entityID, entity);
        }
        task.progress();
      }
    }
    task.finish();
  }
  
  private void loadStructure(TaskProgressLogger task) {
    task.start("Loading project structure");
    
    try (SelectQuery query = exec.makeSelectQuery(RelationsTable.TABLE)) {
      query.addSelects(RelationsTable.LHS_EID, RelationsTable.RHS_EID);
      query.andWhere(RelationsTable.PROJECT_ID.compareEquals(projectID), RelationsTable.RELATION_TYPE.compareEquals(Relation.HAS_BASE_TYPE));
      Map<Integer, Integer> pMapping = new HashMap<>();
      
      task.start("Loading has_base_type relations", "relations loaded");
      TypedQueryResult result = query.selectStreamed();
      while (result.next()) {
        pMapping.put(result.getResult(RelationsTable.LHS_EID), result.getResult(RelationsTable.RHS_EID));
        task.progress();
      }
      task.finish();
      
      task.start("Loading extends/implements relations", "relations loaded");
      query.clearWhere();
      query.andWhere(RelationsTable.PROJECT_ID.compareEquals(projectID), RelationsTable.RELATION_TYPE.compareIn(EnumSet.of(Relation.EXTENDS, Relation.IMPLEMENTS)));
      
      result = query.selectStreamed();
      while (result.next()) {
        Integer lhsEID = result.getResult(RelationsTable.LHS_EID);
        Integer rhsEID = result.getResult(RelationsTable.RHS_EID);
        Integer altRHS = pMapping.get(rhsEID);
        if (altRHS != null) {
          rhsEID = altRHS;
        }
        ModeledEntity child = reverseMap.get(lhsEID);
        if (child == null) {
          logger.severe("Missing child from map: " + lhsEID);
          continue;
        }
        ModeledEntity parent = reverseMap.get(rhsEID);
        // Check the java library
        if (parent == null) {
          parent = libraryModel.getEntity(rhsEID);
        }
        if (parent == null) {
          logger.severe("Missing parent from map: " + rhsEID);
          continue;
        }
        child.addParent(parent);
        task.progress();
      }
      task.finish();
    }
    reverseMap = null;
    libraryModel.clearReverseMap();
    task.finish();
  }
  
  public static ProjectTypeModel makeProjectTypeModel(final TaskProgressLogger task, QueryExecutor exec, Integer projectID, Collection<Integer> libraries, JavaLibraryTypeModel javaModel, UnknownEntityCache unknowns) {
    LibraryTypeModel libraryModel = LibraryTypeModel.makeLibraryTypeModel(task, exec, libraries, javaModel);
    
    task.start("Building project type model");
    
    ProjectTypeModel model = new ProjectTypeModel(exec, projectID, libraryModel, unknowns);
    model.loadEntities(task);
    
    task.finish();
    
    return model;
  }
  
  public static ProjectTypeModel makeVirtualProjectTypeModel(final TaskProgressLogger task, QueryExecutor exec, Integer projectID, Collection<Integer> libraries, JavaLibraryTypeModel javaModel, UnknownEntityCache unknowns) {
    LibraryTypeModel libraryModel = LibraryTypeModel.makeVirtualLibraryTypeModel(task, exec, libraries, javaModel);
    
    task.start("Building virtual project type model");
    
    ProjectTypeModel model = new ProjectTypeModel(exec, projectID, libraryModel, unknowns);
    model.reverseMap = new HashMap<>();
    model.loadEntities(task);
    model.loadStructure(task);
    
    task.finish();
    
    return model;
  }
  
  private ModeledEntity getTypeEntity(String fqn) {
    if (TypeUtils.isArray(fqn)) {
      Pair<String, Integer> arrayInfo = TypeUtils.breakArray(fqn);
      
      // Insert the array entity
      Integer entityID = exec.insertWithKey(EntitiesTable.makeInsert(Entity.ARRAY, fqn, arrayInfo.getSecond(), projectID));
      ModeledEntity entity = new ModeledEntity(fqn, Entity.ARRAY, entityID, RelationClass.NOT_APPLICABLE);
      add(fqn, entity);
      
      // Get the component type
      ModeledEntity component = getEntity(arrayInfo.getFirst());

      // Add has elements of relation
      exec.insert(RelationsTable.makeInsert(Relation.HAS_ELEMENTS_OF, component.getRelationClass(), entityID, component.getEntityID(), projectID));
  
      return entity;
    }
    
    if (TypeUtils.isWildcard(fqn)) {
      // Insert the wildcard entity
      Integer entityID = exec.insertWithKey(EntitiesTable.makeInsert(Entity.WILDCARD, fqn, null, projectID));
      ModeledEntity entity = new ModeledEntity(fqn, Entity.WILDCARD, entityID, RelationClass.NOT_APPLICABLE);
      
      // If it's bounded, add the bound relation
      if (!TypeUtils.isUnboundedWildcard(fqn)) {
        ModeledEntity bound = getEntity(TypeUtils.getWildcardBound(fqn));
        if (TypeUtils.isLowerBound(fqn)) {
          exec.insert(RelationsTable.makeInsert(Relation.HAS_LOWER_BOUND, bound.getRelationClass(), entityID, bound.getEntityID(), projectID));
        } else {
          exec.insert(RelationsTable.makeInsert(Relation.HAS_UPPER_BOUND, bound.getRelationClass(), entityID, bound.getEntityID(), projectID));
        }
      }
      
      return entity;
    }
    
    if (TypeUtils.isTypeVariable(fqn)) {
      // Insert the type variable entity
      Integer entityID = exec.insertWithKey(EntitiesTable.makeInsert(Entity.TYPE_VARIABLE, fqn, null, projectID));
      ModeledEntity entity = new ModeledEntity(fqn, Entity.TYPE_VARIABLE, entityID, RelationClass.NOT_APPLICABLE);
      
      // Insert the bound relations
      for (String bound : TypeUtils.breakTypeVariable(fqn)) {
        ModeledEntity boundEntity = getEntity(bound);
        exec.insert(RelationsTable.makeInsert(Relation.HAS_UPPER_BOUND, boundEntity.getRelationClass(), entityID, boundEntity.getEntityID(), projectID));
      }
      
      return entity;
    }
    
    if (TypeUtils.isParametrizedType(fqn)) {
      // Insert the parametrized type entity
      Integer entityID = exec.insertWithKey(EntitiesTable.makeInsert(Entity.PARAMETERIZED_TYPE, fqn, null, projectID));
      ModeledEntity entity = new ModeledEntity(fqn, Entity.PARAMETERIZED_TYPE, entityID, RelationClass.NOT_APPLICABLE);
      
      // Add the has base type relation
      ModeledEntity baseType = getEntity(TypeUtils.getBaseType(fqn));
      exec.insert(RelationsTable.makeInsert(Relation.HAS_BASE_TYPE, baseType.getRelationClass(), entityID, baseType.getEntityID(), projectID));
      
      // Insert the type arguments
      for (String arg : TypeUtils.breakParametrizedType(fqn)) {
        ModeledEntity argEntity = getEntity(arg);
        exec.insert(RelationsTable.makeInsert(Relation.HAS_TYPE_ARGUMENT, argEntity.getRelationClass(), entityID, argEntity.getEntityID(), projectID));
      }
      
      return entity; 
    }
    
    return null;
  }

  private ModeledEntity getBasicEntity(String fqn) {
    ModeledEntity entity = entities.get(fqn);
    if (entity == null) {
      return libraryModel.getEntity(fqn);
    } else {
      return entity;
    }
  }
  
  public ModeledEntity getEntity(String fqn) {
    ModeledEntity entity = entities.get(fqn);
    if (entity == null && !TypeUtils.isMethod(fqn)) {
      entity = getTypeEntity(fqn);
    }
    if (entity == null) {
      entity = libraryModel.getEntity(fqn);
    }
    if (entity == null) {
      entity = unknowns.getUnknown(exec, fqn);
    }
    return entity;
  }
  
  public ModeledEntity getVirtualEntity(String fqn) {
    // Try the map
    ModeledEntity entity = entities.get(fqn);
    if (entity != null) {
      return entity;
    }
    
    // Try the library model
    entity = libraryModel.getEntity(fqn);
    if (entity != null) {
      return entity;
    }
    
    // Is it an method or a field?
    if (TypeUtils.isMethod(fqn)) {
      Pair<String, String> parts = TypeUtils.breakMethod(fqn);
      
      // No resolution for constructors
      if (parts.getSecond().startsWith("<init>") || parts.getSecond().startsWith("<clinit>")) {
        entity = unknowns.getUnknown(exec, fqn);
        entities.put(fqn, entity);
        return entity;
      }

      // If the receiver is an array
      if (TypeUtils.isArray(parts.getFirst())) {
        entity = getBasicEntity("java.lang.Object." + parts.getSecond());
        if (entity == null) {
          entity = unknowns.getUnknown(exec, fqn);
        }
        entities.put(fqn, entity);
        return entity;
      }
      
      // Can we find the receiver type?
      ModeledEntity receiver = entities.get(parts.getFirst());
      
      // No receiver, try the java library
      if (receiver == null) {
        entity = libraryModel.getVirtualEntity(fqn);
        if (entity == null) {
          entity = unknowns.getUnknown(exec, fqn);
        }
        entities.put(fqn, entity);
        return entity;
      } else {
        ModeledEntity classMethod = null;
        Collection<ModeledEntity> interfaceMethods = new LinkedList<>();
        
        Deque<ModeledEntity> stack = new LinkedList<>();
        stack.push(receiver);
        while (!stack.isEmpty()) {
          // Get all the parents
          for (ModeledEntity parent : stack.pop().getParents()) {
            // See if the parent has the method
            ModeledEntity method = getBasicEntity(parent.getFQN() + "." + parts.getSecond());
            if (method == null) {
              stack.add(parent);
            } else if (parent.getType() == Entity.INTERFACE) {
              interfaceMethods.add(method);
            } else if (classMethod == null){
              classMethod = method;
            } else {
              logger.severe("Multiple class methods for: " + fqn);
            }
          }
        }
        
        if (classMethod == null && interfaceMethods.isEmpty()) {
          entity = unknowns.getUnknown(exec, fqn);
          entities.put(fqn, entity);
          return entity;
        } else if (classMethod != null) {
          entities.put(fqn, classMethod);
          return classMethod;
        } else {
          entity = new ModeledEntity();
          for (ModeledEntity method : interfaceMethods) {
            entity.addVirtualDuplicate(method);
          }
          entities.put(fqn, entity);
          return entity;
        }
      }
    } else {
      int dot = fqn.lastIndexOf('.');
      String receiverFQN = fqn.substring(0, dot);
      String fieldName = fqn.substring(dot + 1);
      
      // Can we find the receiver type?
      ModeledEntity receiver = entities.get(receiverFQN);
      
      // No receiver, no virtual resolution
      if (receiver == null) {
        entity = libraryModel.getVirtualEntity(receiverFQN);
        if (entity == null) {
          entity = unknowns.getUnknown(exec, receiverFQN);
        }
        entities.put(fqn, entity);
        return entity;
      } else {
        Collection<ModeledEntity> fields = new LinkedList<>();
        
        Deque<ModeledEntity> stack = new LinkedList<>();
        stack.push(receiver);
        while (!stack.isEmpty()) {
          // Get all the parents
          for (ModeledEntity parent : stack.pop().getParents()) {
            // See if the parent has the method
            ModeledEntity field = getBasicEntity(parent.getFQN() + "." + fieldName);
            if (field == null) {
              stack.add(parent);
            } else {
              fields.add(field);
            }
          }
        }
        
        if (fields.isEmpty()) {
          entity = unknowns.getUnknown(exec, fqn);
          entities.put(fqn, entity);
          return entity;
        } else if (fields.size() == 1) {
          entity = fields.iterator().next();
          entities.put(fqn, entity);
          return entity;
        } else { 
          logger.severe("Virtual field resolution should never be ambiguous: " + fqn);
          entity = new ModeledEntity();
          for (ModeledEntity field : fields) {
            entity.addVirtualDuplicate(field);
          }
          entities.put(fqn, entity);
          return entity;
        }
      }
    }
  }
}