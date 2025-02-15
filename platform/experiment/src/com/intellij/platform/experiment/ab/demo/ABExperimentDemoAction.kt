// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.experiment.ab.demo

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.platform.experiment.ab.impl.bundle.ABExperimentBundle
import com.intellij.platform.experiment.ab.impl.experiment.getABExperimentInstance
import com.intellij.platform.experiment.ab.impl.option.isControlOptionEnabled

internal class ABExperimentDemoAction : AnAction(ABExperimentBundle.message("experiment.ab.demo.action.name")) {
  override fun actionPerformed(e: AnActionEvent) {
    val service = getABExperimentInstance()

    println("User experiment option is: " + service.getUserExperimentOption())
    println("Is control experiment option enabled (check by service): " + service.isControlExperimentOptionEnabled())
    println("Is control experiment option enabled (check by option itself): " + isControlOptionEnabled())
  }
}