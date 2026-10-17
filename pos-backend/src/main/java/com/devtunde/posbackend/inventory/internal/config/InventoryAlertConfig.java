package com.devtunde.posbackend.inventory.internal.config;

import java.time.ZoneId;
import java.util.TimeZone;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.CronTrigger;

import com.devtunde.posbackend.inventory.internal.scheduler.LowStockAlertScheduler;

@Configuration
public class InventoryAlertConfig implements SchedulingConfigurer {

    private final LowStockAlertProperties properties;
    private final LowStockAlertScheduler scheduler;

    public InventoryAlertConfig(LowStockAlertProperties properties, LowStockAlertScheduler scheduler) {
        this.properties = properties;
        this.scheduler = scheduler;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addTriggerTask(
                scheduler::scanAndAlert,
                new CronTrigger(properties.cron(), TimeZone.getTimeZone(ZoneId.of(properties.zone()))));
    }
}
