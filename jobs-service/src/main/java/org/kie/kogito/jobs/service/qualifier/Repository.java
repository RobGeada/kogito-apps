/*
 * Copyright 2019 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.kie.kogito.jobs.service.qualifier;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.Objects;

import javax.enterprise.util.AnnotationLiteral;
import javax.inject.Qualifier;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Qualifier
@Retention(RUNTIME)
@Target({TYPE, METHOD, FIELD, PARAMETER})
public @interface Repository {

    String value();

    final class Literal extends AnnotationLiteral<Repository> implements Repository {

        private String value;

        public Literal(String value) {
            this.value = value;
        }

        @Override
        public String value() {
            return value;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Literal)) {
                return false;
            }
            if (!super.equals(o)) {
                return false;
            }
            Literal literal = (Literal) o;
            return value.equals(literal.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), value);
        }
    }
}
