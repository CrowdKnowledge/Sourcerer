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
package edu.uci.ics.sourcerer.tools.java.cloning.method.fingerprint;


/**
 * @author Joel Ossher (jossher@uci.edu)
 */
public class JaccardIndex {
  private FingerprintKey key;
  private double index;
  
  public JaccardIndex(FingerprintKey key, double index) {
    this.key = key;
    this.index = index;
  }
  
  public FingerprintKey getFingerprintKey() {
    return key;
  }
  
  public double getIndex() {
    return index;
  }
}
