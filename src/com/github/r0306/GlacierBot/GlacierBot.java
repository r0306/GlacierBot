package com.github.r0306.GlacierBot;

import java.io.IOException;

import javax.security.auth.login.LoginException;

import org.quartz.SchedulerException;

import com.github.r0306.GlacierBot.Connection;

import net.dv8tion.jda.core.exceptions.RateLimitedException;

public class GlacierBot {

	public static final String token = "NDM4NDUwMzgxNTEzNjg3MDQx.DcEyaw.F29F0YeRSV73a83xyUDHcTxdVXI";

	public static void main(String[] args) throws LoginException, IllegalArgumentException, InterruptedException, RateLimitedException, IOException, SchedulerException {
		new Connection(token);
    }
	
}
