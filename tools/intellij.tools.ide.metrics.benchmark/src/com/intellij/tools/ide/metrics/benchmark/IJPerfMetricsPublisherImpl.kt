// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.ide.metrics.benchmark

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.BuildNumber
import com.intellij.openapi.util.io.FileUtil
import com.intellij.platform.testFramework.diagnostic.MetricsAggregation
import com.intellij.platform.testFramework.diagnostic.MetricsPublisher
import com.intellij.platform.testFramework.diagnostic.TelemetryMeterCollector
import com.intellij.teamcity.TeamCityClient
import com.intellij.testFramework.UsefulTestCase
import com.intellij.tools.ide.metrics.collector.OpenTelemetryMeterCollector
import com.intellij.tools.ide.metrics.collector.metrics.MetricsSelectionStrategy
import com.intellij.tools.ide.metrics.collector.metrics.PerformanceMetrics
import com.intellij.tools.ide.metrics.collector.publishing.CIServerBuildInfo
import com.intellij.tools.ide.metrics.collector.publishing.PerformanceMetricsDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.io.path.Path

/**
 * Metrics will be stored as TeamCity artifacts and later will be collected by IJ Perf collector (~ once/twice per hour).
 * Charts can be found at [IJ Perf Dashboard](https://ij-perf.labs.jb.gg/intellij/testsDev) - link is prone to change, though.
 */
class IJPerfMetricsPublisherImpl : MetricsPublisher {
  companion object {
    // for local testing
    private fun setBuildParams(vararg buildProperties: Pair<String, String>): Path {
      val tempPropertiesFile = FileUtil.createTempFile("teamcity_", "_properties_file.properties")

      Properties().apply {
        setProperty("teamcity.build.id", "225659992")
        setProperty("teamcity.buildType.id", "bt3989238923")
        setProperty("teamcity.agent.jvm.os.name", "Linux")

        buildProperties.forEach { this.setProperty(it.first, it.second) }

        store(tempPropertiesFile.outputStream(), "")
      }

      return tempPropertiesFile.toPath()
    }

    private val teamCityClient = TeamCityClient(
      systemPropertiesFilePath =
      // ignoring TC system properties for local test run
      if (UsefulTestCase.IS_UNDER_TEAMCITY) Path(System.getenv("TEAMCITY_BUILD_PROPERTIES_FILE"))
      else setBuildParams()
    )

    private suspend fun prepareMetricsForPublishing(uniqueTestIdentifier: String, vararg metricsCollectors: TelemetryMeterCollector): PerformanceMetricsDto {
      val metrics: List<PerformanceMetrics.Metric> = SpanMetricsExtractor().waitTillMetricsExported(uniqueTestIdentifier)
      val additionalMetrics: List<PerformanceMetrics.Metric> = metricsCollectors.flatMap {
        it.convertToCompleteMetricsCollector().collect(PathManager.getLogDir())
      }

      val mergedMetrics = metrics.plus(additionalMetrics)

      teamCityClient.publishTeamCityArtifacts(source = PathManager.getLogDir(), artifactPath = uniqueTestIdentifier)
      teamCityClient.publishTeamCityArtifacts(source = MetricsPublisher.getIdeTestLogFile(), artifactPath = uniqueTestIdentifier)

      val buildInfo = CIServerBuildInfo(
        buildId = teamCityClient.buildId,
        typeId = teamCityClient.buildTypeId,
        configName = teamCityClient.configurationName ?: "",
        buildNumber = teamCityClient.buildNumber,
        branchName = teamCityClient.branchName,
        url = String.format("%s/viewLog.html?buildId=%s&buildTypeId=%s", teamCityClient.baseUri,
                            teamCityClient.buildId,
                            teamCityClient.buildTypeId),
        isPersonal = teamCityClient.isPersonalBuild,
        timestamp = ZonedDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
      )

      return PerformanceMetricsDto.create(
        projectName = uniqueTestIdentifier,
        projectURL = "",
        projectDescription = "",
        methodName = uniqueTestIdentifier,
        buildNumber = BuildNumber.currentVersion(),
        metrics = mergedMetrics,
        buildInfo = buildInfo
      )
    }
  }

  override suspend fun publish(uniqueTestIdentifier: String, vararg metricsCollectors: TelemetryMeterCollector) {
    val metricsDto = prepareMetricsForPublishing(uniqueTestIdentifier, *metricsCollectors)

    withContext(Dispatchers.IO) {
      val artifactName = "metrics.performance.json"
      val reportFile = Files.createTempFile("unit-perf-metric", artifactName)
      jacksonObjectMapper().writerWithDefaultPrettyPrinter().writeValue(reportFile.toFile(), metricsDto)

      // Print metrics in stdout when running locally
      // https://youtrack.jetbrains.com/issue/AT-644/Performance-tests-do-not-check-anything#focus=Comments-27-8578186.0-0
      // https://youtrack.jetbrains.com/issue/AT-726
      if (!UsefulTestCase.IS_UNDER_TEAMCITY) {
        println("Collected metrics: (can be found in ${teamCityClient.artifactForPublishingDir.resolve(uniqueTestIdentifier).toUri()})")
        println(metricsDto.metrics.joinToString(separator = System.lineSeparator()) { String.format("%-60s %6s", it.n, it.v) })
      }

      teamCityClient.publishTeamCityArtifacts(source = reportFile,
                                              artifactPath = uniqueTestIdentifier,
                                              artifactName = "metrics.performance.json",
                                              zipContent = false)
    }
  }
}

internal fun TelemetryMeterCollector.convertToCompleteMetricsCollector(): OpenTelemetryMeterCollector {
  val metricsSelectionStrategy = when (this.metricsAggregation) {
    MetricsAggregation.EARLIEST -> MetricsSelectionStrategy.EARLIEST
    MetricsAggregation.LATEST -> MetricsSelectionStrategy.LATEST
    MetricsAggregation.MINIMUM -> MetricsSelectionStrategy.MINIMUM
    MetricsAggregation.MAXIMUM -> MetricsSelectionStrategy.MAXIMUM
    MetricsAggregation.SUM -> MetricsSelectionStrategy.SUM
    MetricsAggregation.AVERAGE -> MetricsSelectionStrategy.AVERAGE
  }

  return OpenTelemetryMeterCollector(metricsSelectionStrategy) { meter ->
    this.metersFilter(
      object : Map.Entry<String, List<Long>> {
        override val key: String = meter.key
        override val value: List<Long> = meter.value.map { it.value }
      })
  }
}