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

import org.apache.logging.log4j.util.Strings;

public class ShellOutput {
    public static final ShellOutput EMPTY_SHELL_OUTPUT = new ShellOutput(-1, Strings.EMPTY, Strings.EMPTY);
    private static final int MAXIMUM_OUTPUT_LENGTH = 128;
    private int exitCode;
    private String stdOut;
    private String stdErr;

    public ShellOutput (int exitCode, String stdOut, String stdErr) {
        this(exitCode, stdOut);
        this.stdErr = stdErr;
    }

    public ShellOutput (int exitCode, String stdOut) {
        this.exitCode = exitCode;
        this.stdOut = stdOut;
    }

    public static ShellOutputBuilder builder () {
        return ShellOutputBuilder.aShellOutput();
    }

    public int getExitCode () {
        return exitCode;
    }

    public void setExitCode (int exitCode) {
        this.exitCode = exitCode;
    }

    public String getStdOut () {
        return stdOut;
    }

    public void setStdOut (String stdOut) {
        this.stdOut = stdOut;
    }

    public String getStdErr () {
        return stdErr;
    }

    public void setStdErr (String stdErr) {
        this.stdErr = stdErr;
    }

    @Override
    public String toString () {
        StringBuilder stringBuilder = new StringBuilder();
        if (exitCode == 0) {
            stringBuilder.append(stdOut);
        } else {
            stringBuilder.append(exitCode).append(": ").append(Strings.isNotBlank(stdErr) ? stdErr : stdOut);
        }
        String output = stringBuilder.toString();
        if (output.length() > MAXIMUM_OUTPUT_LENGTH) {
            output = output.substring(0, MAXIMUM_OUTPUT_LENGTH - 3) + "...";
        }
        return output;
    }

    public static final class ShellOutputBuilder {
        private int exitCode;
        private String stdOut;
        private String stdErr;

        private ShellOutputBuilder () {
        }

        public static ShellOutputBuilder aShellOutput () {
            return new ShellOutputBuilder();
        }

        public ShellOutputBuilder withExitCode (int exitCode) {
            this.exitCode = exitCode;
            return this;
        }

        public ShellOutputBuilder withStdOut (String stdOut) {
            this.stdOut = stdOut;
            return this;
        }

        public ShellOutputBuilder withStdErr (String stdErr) {
            this.stdErr = stdErr;
            return this;
        }

        public ShellOutput build () {
            ShellOutput shellOutput = new ShellOutput(exitCode, stdOut);
            shellOutput.setStdErr(stdErr);
            return shellOutput;
        }
    }
}
