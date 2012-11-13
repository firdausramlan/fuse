/**
 * Copyright (C) FuseSource, Inc.
 * http://fusesource.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.fusesource.insight.log.log4j;

import org.apache.log4j.Appender;
import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.Category;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.spi.AppenderAttachable;
import org.apache.log4j.spi.LocationInfo;
import org.apache.log4j.spi.LoggingEvent;
import org.apache.log4j.spi.ThrowableInformation;
import org.fusesource.insight.log.LogEvent;
import org.fusesource.insight.log.LogFilter;
import org.fusesource.insight.log.LogResults;
import org.fusesource.insight.log.support.LogQuerySupport;
import org.fusesource.insight.log.support.LruList;
import org.fusesource.insight.log.support.Predicate;
import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.fusesource.insight.log.support.Strings.contains;

/**
 * A log4j adapter for LogQueryMBean
 */
public class Log4jLogQuery extends LogQuerySupport implements Log4jLogQueryMBean {
    private static final transient Logger LOG = LoggerFactory.getLogger(Log4jLogQuery.class);

    private int size = 1000;
    private LruList<LoggingEvent> events;

    @PostConstruct
    public void start() {
        super.start();
        ILoggerFactory loggerFactory = LoggerFactory.getILoggerFactory();
        AppenderAttachable appenderAttachable = null;
        if (loggerFactory instanceof AppenderAttachable) {
            appenderAttachable = (AppenderAttachable) loggerFactory;
        }
        if (appenderAttachable == null) {
            appenderAttachable = LogManager.getRootLogger();
        }
        if (appenderAttachable != null) {
            Appender appender = new AppenderSkeleton() {
                protected void append(LoggingEvent loggingEvent) {
                    getEvents().add(loggingEvent);
                }

                public void close() {
                }

                public boolean requiresLayout() {
                    return true;
                }
            };
            appender.setName("LogQuery");
            appenderAttachable.addAppender(appender);
        } else {
            LOG.error("No ILoggerFactory found so cannot attach appender!");
        }
    }

    @PreDestroy
    public void stop() {
        super.stop();
    }

    public LogResults getLogResults(int maxCount) throws IOException {
        return filterLogResults(null, maxCount);
    }

    public LogResults queryLogResults(LogFilter filter) {
        Predicate<LogEvent> predicate = createPredicate(filter);
        int maxCount = -1;
        if (filter != null) {
            maxCount = filter.getCount();
        }
        return filterLogResults(predicate, maxCount);
    }

    private Predicate<LogEvent> createPredicate(LogFilter filter) {
    if (filter == null) {
                return null;
            }
            final List<Predicate<LogEvent>> predicates = new ArrayList<Predicate<LogEvent>>();
    
            final Set<String> levels = filter.getLevelsSet();
            if (levels.size() > 0) {
                predicates.add(new Predicate<LogEvent>() {
                    @Override
                    public boolean matches(LogEvent event) {
                        String level = event.getLevel();
                        return level != null && levels.contains(level.toString());
                    }
                });
            }
            final Long before = filter.getBeforeTimestamp();
            if (before != null) {
                final Date date = new Date(before);
                predicates.add(new Predicate<LogEvent>() {
                    @Override
                    public boolean matches(LogEvent event) {
                        Date time = event.getTimestamp();
                        return time != null && time.before(date);
                    }
                });
            }
            final Long after = filter.getAfterTimestamp();
            if (after != null) {
                final Date date = new Date(after);
                predicates.add(new Predicate<LogEvent>() {
                    @Override
                    public boolean matches(LogEvent event) {
                        Date time = event.getTimestamp();
                        return time != null && time.after(date);
                    }
                });
            }
    
            final String matchesText = filter.getMatchesText();
            if (matchesText != null && matchesText.length() > 0) {
                predicates.add(new Predicate<LogEvent>() {
                    @Override
                    public boolean matches(LogEvent event) {
                        if (contains(matchesText, event.getClassName(), event.getMessage(), event.getLogger(), event.getThread())) {
                            return true;
                        }
                        String[] throwableStrRep = event.getException();
                        if (throwableStrRep != null && contains(matchesText, throwableStrRep)) {
                            return true;
                        }
                        Map properties = event.getProperties();
                        if (properties != null && contains(matchesText, properties.toString())) {
                            return true;
                        }
                        return false;
                    }
                });
            }
    
            if (predicates.size() == 0) {
                return null;
            } else if (predicates.size() == 1) {
                return predicates.get(0);
            } else {
                return new Predicate<LogEvent>() {
                    @Override
                    public String toString() {
                        return "AndPredicate" + predicates;
                    }
    
                    @Override
                    public boolean matches(LogEvent event) {
                        for (Predicate<LogEvent> predicate : predicates) {
                            if (!predicate.matches(event)) {
                                return false;
                            }
                        }
                        return true;
                    }
                };
            }
    }

    protected LogResults filterLogResults(Predicate<LogEvent> predicate, int maxCount) {
        int matched = 0;
        long from = Long.MAX_VALUE;
        long to = Long.MIN_VALUE;
        List<LogEvent> list = new ArrayList<LogEvent>();
        Iterable<LoggingEvent> elements = getEvents().getElements();
        for (LoggingEvent element : elements) {
            LogEvent logEvent = toLogEvent(element);
            long timestamp = element.getTimeStamp();
            if (timestamp > to) {
                to = timestamp;
            }
            if (timestamp < from) {
                from = timestamp;
            }
            if (logEvent != null) {
                if (predicate == null || predicate.matches(logEvent)) {
                    list.add(logEvent);
                    matched += 1;
                    if (maxCount > 0 && matched >= maxCount) {
                        break;
                    }
                }
            }
        }
        LogResults results = new LogResults();
        results.setEvents(list);
        results.setFromTimestamp(from);
        results.setToTimestamp(to);
        if (LOG.isDebugEnabled()) {
            LOG.debug("Requested " + maxCount + " logging items. returning "
                    + results.getEvents().size() + " event(s) from a possible " + getEvents().size());

        }
        return results;
    }

    protected LogEvent toLogEvent(LoggingEvent element) {
        LogEvent answer = new LogEvent();
        answer.setClassName(element.getFQNOfLoggerClass());
        // TODO
        //answer.setContainerName(element.get);
        ThrowableInformation throwableInformation = element.getThrowableInformation();
        if (throwableInformation != null) {
            answer.setException(throwableInformation.getThrowableStrRep());
        }
        LocationInfo locationInformation = element.getLocationInformation();
        if (locationInformation != null) {
            answer.setFileName(locationInformation.getFileName());
            answer.setClassName(locationInformation.getClassName());
            answer.setMethodName(locationInformation.getMethodName());
            answer.setLineNumber(locationInformation.getLineNumber());
        }
        Level level = element.getLevel();
        if (level != null) {
            answer.setLevel(level.toString());
        }
        // TODO
        answer.setLogger(element.getLoggerName());
        Category logger = element.getLogger();
        Object message = element.getMessage();
        if (message != null) {
            // TODO marshal differently?
            answer.setMessage(message.toString());
        }
        answer.setProperties(element.getProperties());
        // TODO
        answer.setSeq(element.getTimeStamp());
        answer.setTimestamp(new Date(element.getTimeStamp()));
        answer.setThread(element.getThreadName());
        answer.setHost(getHostName());
        return answer;
    }

    protected String filterLogEvents(LogFilter filter) throws IOException {
        // TODO
        return null;
    }

    // Properties
    //-------------------------------------------------------------------------
    public LruList<LoggingEvent> getEvents() {
        if (events == null) {
            events = new LruList<LoggingEvent>(LoggingEvent.class, getSize());
        }
        return events;
    }

    public void setEvents(LruList<LoggingEvent> events) {
        this.events = events;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }
}
