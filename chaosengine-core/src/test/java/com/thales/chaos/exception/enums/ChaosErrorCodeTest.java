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

package com.thales.chaos.exception.enums;

import org.hamcrest.CoreMatchers;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collection;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertThat;

public class ChaosErrorCodeTest {
    private Collection<ChaosErrorCode> allChaosErrorCodes;

    @Before
    public void setUp () {
        allChaosErrorCodes = Arrays.asList(ChaosErrorCode.values());
    }

    @Test
    public void getMessage () {
        allChaosErrorCodes.stream()
                          .peek(errorCode -> assertThat(errorCode.getMessage(), containsString(Integer.toString(errorCode
                                  .getErrorCode()))))
                          .peek(errorCode -> assertThat(errorCode.getMessage(), CoreMatchers.endsWith(".message")))
                          .forEach(errorCode -> assertThat(errorCode.getMessage(), CoreMatchers.startsWith("errorCode.")));
    }
}