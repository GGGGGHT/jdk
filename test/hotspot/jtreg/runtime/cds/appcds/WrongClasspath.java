/*
 * Copyright (c) 2014, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 *
 */

/*
 * @test
 * @summary classpath mismatch between dump time and execution time
 * @requires vm.cds
 * @library /test/lib
 * @compile test-classes/Hello.java
 * @compile test-classes/C2.java
 * @run driver WrongClasspath
 */

import java.io.File;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.helpers.ClassFileInstaller;

public class WrongClasspath {

  public static void main(String[] args) throws Exception {
    String appJar = JarBuilder.getOrCreateHelloJar();
    String unableToUseMsg = "Unable to use shared archive";
    String mismatchMsg = "shared class paths mismatch (hint: enable -Xlog:class+path=info to diagnose the failure)";

    // Dump an archive with a specified JAR file in -classpath
    TestCommon.testDump(appJar, TestCommon.list("Hello"));

    // Then try to execute the archive without -classpath -- it should fail
    TestCommon.run(
        /* "-cp", appJar, */ // <- uncomment this and the execution should succeed
        "-Xlog:cds",
        "Hello")
        .assertAbnormalExit(unableToUseMsg, mismatchMsg);

    // Run with -Xshare:auto and without CDS logging enabled, the mismatch message
    // should still be there.
    OutputAnalyzer output = TestCommon.execAuto("Hello");
    output.shouldContain(mismatchMsg);

    // Dump CDS archive with 2 jars: -cp hello.jar:jar2.jar
    // Run with 2 jars but the second jar doesn't exist: -cp hello.jarjar2.jarx
    // Shared class paths mismatch should be detected.
    String jar2 = ClassFileInstaller.writeJar("jar2.jar", "pkg/C2");
    String jars = appJar + File.pathSeparator + jar2;
    TestCommon.testDump(jars, TestCommon.list("Hello", "pkg/C2"));
    TestCommon.run(
        "-cp", jars + "x", "Hello")
        .assertAbnormalExit(unableToUseMsg, mismatchMsg);

    // modify the timestamp of the jar2
    (new File(jar2.toString())).setLastModified(System.currentTimeMillis() + 2000);

    // Run with -Xshare:auto and without CDS logging enabled, the "timestamp has changed"
    // message should be there.
    output = TestCommon.execAuto(
        "-cp", jars, "Hello");
    output.shouldMatch(".warning..cds. A jar file is not the one used while building the shared archive file:.*jar2.jar")
          .shouldMatch(".warning..cds.*jar2.jar timestamp has changed.");
  }
}
