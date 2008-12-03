/*
 * Copyright 2006-2007 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.batch.core.step.tasklet;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.job.JobSupport;
import org.springframework.batch.core.repository.dao.MapJobExecutionDao;
import org.springframework.batch.core.repository.dao.MapJobInstanceDao;
import org.springframework.batch.core.repository.dao.MapStepExecutionDao;
import org.springframework.batch.core.step.JobRepositorySupport;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemStreamSupport;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.support.ListItemReader;
import org.springframework.batch.repeat.policy.SimpleCompletionPolicy;
import org.springframework.batch.repeat.support.RepeatTemplate;
import org.springframework.batch.repeat.support.TaskExecutorRepeatTemplate;
import org.springframework.batch.support.transaction.ResourcelessTransactionManager;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.util.StringUtils;

public class AsyncTaskletStepTests {

	private List<String> processed = new ArrayList<String>();

	private TaskletStep step;

	private JobInstance jobInstance;

	ItemWriter<String> itemWriter = new ItemWriter<String>() {
		public void write(List<? extends String> data) throws Exception {
			processed.addAll(data);
		}
	};

	@Before
	public void setUp() throws Exception {
		MapJobInstanceDao.clear();
		MapStepExecutionDao.clear();
		MapJobExecutionDao.clear();

		step = new TaskletStep("stepName");

		ResourcelessTransactionManager transactionManager = new ResourcelessTransactionManager();
		step.setTransactionManager(transactionManager);

		List<String> items = Arrays.asList(StringUtils
				.commaDelimitedListToStringArray("1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25"));
		RepeatTemplate chunkTemplate = new RepeatTemplate();
		chunkTemplate.setCompletionPolicy(new SimpleCompletionPolicy(2));
		step.setTasklet(new TestingChunkOrientedTasklet<String>(new ListItemReader<String>(items), itemWriter, chunkTemplate));

		step.setJobRepository(new JobRepositorySupport());

		TaskExecutorRepeatTemplate template = new TaskExecutorRepeatTemplate();
		template.setTaskExecutor(new SimpleAsyncTaskExecutor());
		step.setStepOperations(template);

		step.registerStream(new ItemStreamSupport() {
			private int count = 0;

			@Override
			public void update(ExecutionContext executionContext) {
				executionContext.putInt("counter", count++);
			}
		});

		JobSupport job = new JobSupport("FOO");
		jobInstance = new JobInstance(0L, new JobParameters(), job.getName());

	}

	/**
	 * StepExecution should be updated after every chunk commit.
	 */
	@Test
	public void testStepExecutionUpdates() throws Exception {

		JobExecution jobExecution = new JobExecution(jobInstance);
		StepExecution stepExecution = jobExecution.createStepExecution(step.getName());

		step.execute(stepExecution);

		assertEquals(25, processed.size());
		assertEquals(25, stepExecution.getReadCount());
		assertEquals(BatchStatus.COMPLETED, stepExecution.getStatus());

	}

}