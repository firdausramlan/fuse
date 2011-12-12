/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2011 FuseSource Corporation, a Progress Software company. All rights reserved.
 *
 * The contents of this file are subject to the terms of the Common Development
 * and Distribution License("CDDL") (the "License").
 * You may not use this file except in compliance with the License. You can obtain
 * a copy of the License at http://www.opensource.org/licenses/CDDL-1.0.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at resources/META-INF/LICENSE.txt.
 *
 */
package org.fusesource.eca.component.statistics;

import java.util.concurrent.BlockingQueue;

import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.seda.SedaEndpoint;
import org.apache.camel.util.ServiceHelper;
import org.fusesource.eca.processor.StatisticsProcessor;

/**
 * Statistics endpoint.
 */
public class StatisticsEndpoint extends SedaEndpoint {

    private StatisticsProcessor statisticsProcessor;
    private String eventWindow = "30s,1000";
    private String batchUpdateTime = "";
    private String cacheImplementation = "default";
    private String cacheId = "";
    private String queryString;
    private String statisticsType = "ALL";

    public StatisticsEndpoint() {
    }

    public StatisticsEndpoint(String endpointUri, Component component, BlockingQueue<Exchange> queue) {
        this(endpointUri, component, queue, 1);
    }

    public StatisticsEndpoint(String endpointUri, Component component, BlockingQueue<Exchange> queue, int concurrentConsumers) {
        super(endpointUri, component, queue, concurrentConsumers);
    }

    public Producer createProducer() throws Exception {
        return new StatisticsProducer(this, getQueue(), getWaitForTaskToComplete(), getTimeout());
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        return new StatisticsConsumer(this, processor);
    }

    public String getEventWindow() {
        return eventWindow;
    }

    public String getWin() {
        return getEventWindow();
    }

    public String getWindow() {
        return getEventWindow();
    }

    public void setEventWindow(String eventWindow) {
        this.eventWindow = eventWindow;
    }

    public void setWin(String win) {
        setEventWindow(win);
    }

    public void setWindow(String window) {
        setEventWindow(window);
    }

    public String getCacheImplementation() {
        return cacheImplementation;
    }

    public void setCacheImplementation(String cacheImplementation) {
        this.cacheImplementation = cacheImplementation;
    }

    public String getQueryString() {
        return queryString;
    }

    public void setQueryString(String queryString) {
        this.queryString = queryString;
    }

    public String getCacheId() {
        return cacheId;
    }

    public void setCacheId(String cacheId) {
        this.cacheId = cacheId;
    }

    public String getBatchUpdateTime() {
        return batchUpdateTime;
    }

    public boolean isPolling() {
        return batchUpdateTime != null && batchUpdateTime.length() > 0;
    }

    public void setBatchUpdateTime(String batchUpdateTime) {
        this.batchUpdateTime = batchUpdateTime;
    }

    public String getStatisticsType() {
        return statisticsType;
    }

    public void setStatisticsType(String statisticsType) {
        this.statisticsType = statisticsType;
    }

    public StatisticsProcessor getStatsProcessor() {
        return statisticsProcessor;
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopService(statisticsProcessor);
        super.doStop();
    }

    @Override
    protected void doStart() throws Exception {
        if (this.statisticsProcessor == null) {
            Processor childProcessor = null;
            this.statisticsProcessor = new StatisticsProcessor(getCamelContext(), childProcessor, getCacheId(), getEventWindow(), getBatchUpdateTime(), getStatisticsType(), getQueryString());
            this.statisticsProcessor.setCacheImplementation(getCacheImplementation());
        }
        ServiceHelper.startService(statisticsProcessor);
        super.doStart();
    }
}
