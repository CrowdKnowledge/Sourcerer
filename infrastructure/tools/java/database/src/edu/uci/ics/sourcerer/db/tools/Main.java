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
package edu.uci.ics.sourcerer.db.tools;

import edu.uci.ics.sourcerer.db.util.DatabaseConnection;
import edu.uci.ics.sourcerer.util.io.Logging;
import edu.uci.ics.sourcerer.util.io.Property;
import edu.uci.ics.sourcerer.util.io.PropertyManager;

/**
 * @author Joel Ossher (jossher@uci.edu)
 */
public class Main {
  public static void main(String[] args) {
   PropertyManager.initializeProperties(args);
   Logging.initializeLogger();
   
   DatabaseConnection connection = new DatabaseConnection();
   connection.open();
   
   PropertyManager properties = PropertyManager.getProperties();
   if (properties.isSet(Property.INITIALIZE_DATABASE)) {
     InitializeDatabase tool = new InitializeDatabase(connection);
     tool.initializeDatabase();
   } else if (properties.isSet(Property.ADD_JARS)) {
     AddJars tool = new AddJars(connection);
     tool.addJars();
   } else if (properties.isSet(Property.ADD_PROJECTS)) {
//     AddProjects tool = new AddProjects(connection);
//     tool.addProjects();
   }
   
   connection.close();
  }
}