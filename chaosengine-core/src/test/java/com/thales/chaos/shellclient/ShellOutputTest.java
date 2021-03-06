/*
 *    Copyright (c) 2018 - 2020, Thales DIS CPL Canada, Inc
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 *
 */

package com.thales.chaos.shellclient;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.List;

import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
public class ShellOutputTest {
    private final String expected;
    private final ShellOutput shellOutput;

    public ShellOutputTest (int exitCode, String stdOut, String stdErr, String expected) {
        this.expected = expected;
        this.shellOutput = ShellOutput.builder().withExitCode(exitCode).withStdOut(stdOut).withStdErr(stdErr).build();
    }

    @Parameterized.Parameters
    public static List<Object[]> parameters () {
        return List.of(new Object[]{ 0, "output", "error", "output" }, new Object[]{ 1, "output", "error", "1: error" }, new Object[]{ 1, "output", "", "1: output" }, new Object[]{ 1, "output", " ", "1: output" }, new Object[]{ 1, "output", null, "1: output" }, new Object[]{ 0, "x".repeat(200), "", "x".repeat(125) + "..." }, new Object[]{ 1, "x".repeat(200), "", "1: " + "x".repeat(122) + "..." }, new Object[]{ 1, "x".repeat(200), " ", "1: " + "x".repeat(122) + "..." }, new Object[]{ 1, "x".repeat(200), null, "1: " + "x".repeat(122) + "..." }, new Object[]{ 1, "", "e".repeat(200), "1: " + "e".repeat(122) + "..." });
    }

    @Test
    public void toStringTest () {
        assertEquals(expected, shellOutput.toString());
    }
}