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
package edu.uci.ics.sourcerer.tools.java.extractor;

import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;

import edu.uci.ics.sourcerer.tools.java.extractor.Extractor.ExtractionMethod;
import edu.uci.ics.sourcerer.tools.java.extractor.Extractor.JarType;
import edu.uci.ics.sourcerer.tools.java.extractor.eclipse.EclipseUtils;
import edu.uci.ics.sourcerer.tools.java.extractor.io.WriterBundle;
import edu.uci.ics.sourcerer.tools.java.extractor.io.internal.CommentWriterImpl;
import edu.uci.ics.sourcerer.tools.java.extractor.io.internal.EntityWriterImpl;
import edu.uci.ics.sourcerer.tools.java.extractor.io.internal.FileWriterImpl;
import edu.uci.ics.sourcerer.tools.java.extractor.io.internal.ImportWriterImpl;
import edu.uci.ics.sourcerer.tools.java.extractor.io.internal.LocalVariableWriterImpl;
import edu.uci.ics.sourcerer.tools.java.extractor.io.internal.MissingTypeWriterImpl;
import edu.uci.ics.sourcerer.tools.java.extractor.io.internal.ProblemWriterImpl;
import edu.uci.ics.sourcerer.tools.java.extractor.io.internal.RelationWriterImpl;
import edu.uci.ics.sourcerer.tools.java.extractor.io.internal.UsedJarWriterImpl;
import edu.uci.ics.sourcerer.tools.java.extractor.missing.MissingTypeIdentifier;
import edu.uci.ics.sourcerer.tools.java.repo.model.JavaRepositoryFactory;
import edu.uci.ics.sourcerer.util.io.logging.Logging;
import edu.uci.ics.sourcerer.util.io.arguments.Argument;
import edu.uci.ics.sourcerer.util.io.arguments.BooleanArgument;
import edu.uci.ics.sourcerer.util.io.arguments.Command;


/**
 * @author Joel Ossher (jossher@uci.edu)
 */
public class Main implements IApplication {
//  public static final Property<Boolean> EXTRACT_LATEST_MAVEN = new BooleanProperty("extract-latest-maven", false, "Extract only the latest maven jars.");
//  public static final Property<Boolean> EXTRACT_BINARY = new BooleanProperty("extract-binary", false, "Extract jars as binary only.");
//  public static final Property<Boolean> USE_PROJECT_JARS = new BooleanProperty("use-project-jars", true, "Use project jars on the classpath.");
//  public static final Property<Boolean> RESOLVE_MISSING_TYPES = new BooleanProperty("resolve-missing-types", false, "Attempt to resolve missing types.")
//      .setRequiredProperties(DATABASE_URL, DATABASE_USER, DATABASE_PASSWORD);
//  public static final Property<Boolean> SKIP_MISSING_TYPES = new BooleanProperty("skip-missing-types", false, "Skip extraction of projects with missing types.");
//  public static final Property<Boolean> FORCE_MISSING_REDO = new BooleanProperty("force-missing-redo", false, "Re-attempt extraction on failed missing type extractions.");
  public static final Argument<Boolean> FORCE_REDO = new BooleanArgument("force-redo", false, "Redo all extractions, even if already completed.");
  
  private static abstract class ExtractorCommand extends Command {
//    protected DatabaseConnection connection = null;
//    protected MissingTypeResolver resolver = null;
    
    public ExtractorCommand(String name, String description) {
      super(name, description);
    }
    
    @Override
    protected void execute() {
      Logging.initializeLogger(this);
      
      WriterBundle.IMPORT_WRITER.setValue(ImportWriterImpl.class);
      WriterBundle.PROBLEM_WRITER.setValue(ProblemWriterImpl.class);
      WriterBundle.ENTITY_WRITER.setValue(EntityWriterImpl.class);
      WriterBundle.LOCAL_VARIABLE_WRITER.setValue(LocalVariableWriterImpl.class);
      WriterBundle.RELATION_WRITER.setValue(RelationWriterImpl.class);
      WriterBundle.COMMENT_WRITER.setValue(CommentWriterImpl.class);
      WriterBundle.FILE_WRITER.setValue(FileWriterImpl.class);
      WriterBundle.USED_JAR_WRITER.setValue(UsedJarWriterImpl.class);
      WriterBundle.MISSING_TYPE_WRITER.setValue(MissingTypeWriterImpl.class);
//
//      if (RESOLVE_MISSING_TYPES.getValue()) {
//        connection = new DatabaseConnection();
//        connection.open();
//        resolver = new MissingTypeResolver(connection);
//      }
      
      action();
      
//      FileUtils.close(connection);
    }
  }
  
  public static final Command ADD_LIBRARIES_TO_REPO =
    new Command("add-libraries-to-repo", "Add the libraries to the repository.") {
      @Override
      protected void action() {
        EclipseUtils.addLibraryJarsToRepository();
      }
    }.setProperties(JavaRepositoryFactory.INPUT_REPO);
  
  public static final Command EXTRACT_LIBRARIES_ECLIPSE =
    new ExtractorCommand("extract-libraries-eclipse", "Extract the libraries using Eclipse.") {
      protected void action() {
        Extractor.extractJars(JarType.LIBRARY, ExtractionMethod.ECLIPSE);
      }
    }.setProperties(JavaRepositoryFactory.INPUT_REPO, JavaRepositoryFactory.OUTPUT_REPO, FORCE_REDO);
    
  public static final Command EXTRACT_LIBRARIES_ASM =
    new ExtractorCommand("extract-libraries-asm", "Extract the libraries using Asm.") {
      protected void action() {
        Extractor.extractJars(JarType.LIBRARY, ExtractionMethod.ASM);
      }
    }.setProperties(JavaRepositoryFactory.INPUT_REPO, JavaRepositoryFactory.OUTPUT_REPO, FORCE_REDO);
    
  public static final Command EXTRACT_LIBRARIES =
    new ExtractorCommand("extract-libraries", "Extract the libraries using Eclipse and Asm.") {
      protected void action() {
        Extractor.extractJars(JarType.LIBRARY, ExtractionMethod.ASM_ECLIPSE);
      }
    }.setProperties(JavaRepositoryFactory.INPUT_REPO, JavaRepositoryFactory.OUTPUT_REPO, FORCE_REDO);
  
  public static final Command EXTRACT_JARS_ECLIPSE =
    new ExtractorCommand("extract-jars-eclipse", "Extract the jars using Eclipse.") {
      protected void action() {
        Extractor.extractJars(JarType.PROJECT, ExtractionMethod.ECLIPSE);
      }
    }.setProperties(JavaRepositoryFactory.INPUT_REPO, JavaRepositoryFactory.OUTPUT_REPO, FORCE_REDO);
    
  public static final Command EXTRACT_JARS_ASM =
    new ExtractorCommand("extract-jars-asm", "Extract the jars using Asm.") {
      protected void action() {
        Extractor.extractJars(JarType.PROJECT, ExtractionMethod.ASM);
      }
    }.setProperties(JavaRepositoryFactory.INPUT_REPO, JavaRepositoryFactory.OUTPUT_REPO, FORCE_REDO);
  
  public static final Command EXTRACT_JARS =
    new ExtractorCommand("extract-jars", "Extract the jars using Eclipse and Asm.") {
      protected void action() {
        Extractor.extractJars(JarType.PROJECT, ExtractionMethod.ASM_ECLIPSE);
      }
    }.setProperties(JavaRepositoryFactory.INPUT_REPO, JavaRepositoryFactory.OUTPUT_REPO, FORCE_REDO);
    
  public static final Command EXTRACT_PROJECTS = 
    new ExtractorCommand("extract-projects", "Extract the projects.") {
      protected void action() {
        Extractor.extractProjects();
      }
    }.setProperties(JavaRepositoryFactory.INPUT_REPO, JavaRepositoryFactory.OUTPUT_REPO, FORCE_REDO, Extractor.INCLUDE_PROJECT_JARS);
    
  public static final Command IDENTIFY_EXTERNAL_TYPES =
    new Command("identify-external-types", "Identified the external types") {
      protected void action() {
        WriterBundle.MISSING_TYPE_WRITER.setValue(MissingTypeWriterImpl.class);
        WriterBundle.IMPORT_WRITER.setValue(ImportWriterImpl.class);
        MissingTypeIdentifier.identifyExternalTypes();
      }
    }.setProperties(JavaRepositoryFactory.INPUT_REPO, JavaRepositoryFactory.OUTPUT_REPO, FORCE_REDO);
    
  public static final Command IDENTIFY_MISSING_TYPES =
    new Command("identify-missing-types", "Identified the missing types") {
      protected void action() {
        WriterBundle.MISSING_TYPE_WRITER.setValue(MissingTypeWriterImpl.class);
        WriterBundle.IMPORT_WRITER.setValue(ImportWriterImpl.class);
        MissingTypeIdentifier.identifyMissingTypes();
      }
    }.setProperties(JavaRepositoryFactory.INPUT_REPO, JavaRepositoryFactory.OUTPUT_REPO, FORCE_REDO);       
  		  
  @Override
  public Object start(IApplicationContext context) throws Exception {
    String[] args = (String[]) context.getArguments().get(IApplicationContext.APPLICATION_ARGS);
    Command.execute(args, Main.class);
    return EXIT_OK;
  }

  @Override
  public void stop() {}
  
  public static void main(String[] args) {
    Command.execute(args, Main.class);
  }
}
