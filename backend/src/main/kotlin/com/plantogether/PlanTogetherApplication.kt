package com.plantogether

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class PlanTogetherApplication

fun main(args: Array<String>) {
    runApplication<PlanTogetherApplication>(*args)
}
