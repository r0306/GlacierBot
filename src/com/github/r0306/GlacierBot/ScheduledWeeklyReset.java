package com.github.r0306.GlacierBot;

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

public class ScheduledWeeklyReset implements Job {

	private Connection connection; 
	
	public ScheduledWeeklyReset(Connection connection) {
		this.connection = connection;
	}
	
	@Override
	public void execute(JobExecutionContext context) throws JobExecutionException {
		connection.resetCheckInWeekly();
	}
	
}
