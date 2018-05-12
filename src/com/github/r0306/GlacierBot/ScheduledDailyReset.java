package com.github.r0306.GlacierBot;

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

public class ScheduledDailyReset implements Job {

	private Connection connection; 
	
	public ScheduledDailyReset(Connection connection) {
		this.connection = connection;
	}
	
	@Override
	public void execute(JobExecutionContext context) throws JobExecutionException {
		connection.resetCheckInDaily();
	}
	
}
