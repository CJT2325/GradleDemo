package com.cjt.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project

class BuryPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.android.registerTransform(new LogTransform())
    }
}
