/*
 * Copyright 2021 Red Hat, Inc. and/or its affiliates.
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

import { ProcessInstance } from '@kogito-apps/management-console-shared';
import { ProcessInstanceFilter, SortBy } from './ProcessListEnvelopeApi';
export interface ProcessListChannelApi {
  processList__initialLoad(
    filter: ProcessInstanceFilter,
    sortBy: SortBy
  ): Promise<void>;
  processList__applyFilter(filter: ProcessInstanceFilter): Promise<void>;
  processList__applySorting(sortBy: SortBy): Promise<void>;
  processList__query(offset: number, limit: number): Promise<ProcessInstance[]>;
  processList__getChildProcessesQuery(
    rootProcessInstanceId: string
  ): Promise<ProcessInstance[]>;
}
