package com.avast.teamcity.plugins.instaclone.buildstep

import jetbrains.buildServer.agent.*
import jetbrains.buildServer.agentServer.AgentBuild
import jetbrains.buildServer.artifacts.ArtifactDependencyInfo
import jetbrains.buildServer.parameters.ValueResolver
import jetbrains.buildServer.util.Option
import jetbrains.buildServer.util.PasswordReplacer
import jetbrains.buildServer.vcs.VcsChangeInfo
import jetbrains.buildServer.vcs.VcsRoot
import jetbrains.buildServer.vcs.VcsRootEntry
import java.io.File

/**
 *
 * @author Vitasek L.
 */
abstract class AgentRunningBuildAdapter: AgentRunningBuild {
    override fun getProjectName(): String {
        TODO("Not yet implemented")
    }

    override fun getBuildTypeId(): String {
        TODO("Not yet implemented")
    }

    override fun getBuildTypeExternalId(): String {
        TODO("Not yet implemented")
    }

    override fun getBuildTypeName(): String {
        TODO("Not yet implemented")
    }

    override fun getBuildId(): Long {
        TODO("Not yet implemented")
    }

    override fun isCleanBuild(): Boolean {
        TODO("Not yet implemented")
    }

    override fun isPersonal(): Boolean {
        TODO("Not yet implemented")
    }

    override fun isPersonalPatchAvailable(): Boolean {
        TODO("Not yet implemented")
    }

    override fun isCheckoutOnAgent(): Boolean {
        TODO("Not yet implemented")
    }

    override fun isCheckoutOnServer(): Boolean {
        TODO("Not yet implemented")
    }

    override fun getCheckoutType(): AgentBuild.CheckoutType {
        TODO("Not yet implemented")
    }

    override fun getExecutionTimeoutMinutes(): Long {
        TODO("Not yet implemented")
    }

    override fun getArtifactDependencies(): MutableList<ArtifactDependencyInfo> {
        TODO("Not yet implemented")
    }

    override fun getAccessUser(): String {
        TODO("Not yet implemented")
    }

    override fun getAccessCode(): String {
        TODO("Not yet implemented")
    }

    override fun getVcsRootEntries(): MutableList<VcsRootEntry> {
        TODO("Not yet implemented")
    }

    override fun getBuildCurrentVersion(vcsRoot: VcsRoot): String {
        TODO("Not yet implemented")
    }

    override fun getBuildPreviousVersion(vcsRoot: VcsRoot): String {
        TODO("Not yet implemented")
    }

    override fun isCustomCheckoutDirectory(): Boolean {
        TODO("Not yet implemented")
    }

    override fun getVcsChanges(): MutableList<VcsChangeInfo> {
        TODO("Not yet implemented")
    }

    override fun getPersonalVcsChanges(): MutableList<VcsChangeInfo> {
        TODO("Not yet implemented")
    }

    override fun getBuildTempDirectory(): File {
        TODO("Not yet implemented")
    }

    override fun getAgentTempDirectory(): File {
        TODO("Not yet implemented")
    }

    override fun getBuildLogger(): BuildProgressLogger {
        TODO("Not yet implemented")
    }

    override fun getAgentConfiguration(): BuildAgentConfiguration {
        TODO("Not yet implemented")
    }

    override fun <T : Any?> getBuildTypeOptionValue(option: Option<T>): T {
        TODO("Not yet implemented")
    }

    override fun getDefaultCheckoutDirectory(): File {
        TODO("Not yet implemented")
    }

    override fun getVcsSettingsHashForCheckoutMode(agentCheckoutMode: AgentCheckoutMode?): String {
        TODO("Not yet implemented")
    }

    override fun getBuildRunners(): MutableList<BuildRunnerSettings> {
        TODO("Not yet implemented")
    }

    override fun describe(verbose: Boolean): String {
        TODO("Not yet implemented")
    }

    override fun getMandatoryBuildParameters(): BuildParametersMap {
        TODO("Not yet implemented")
    }

    override fun getCheckoutDirectory(): File {
        TODO("Not yet implemented")
    }

    override fun getEffectiveCheckoutMode(): AgentCheckoutMode? {
        TODO("Not yet implemented")
    }

    override fun getWorkingDirectory(): File {
        TODO("Not yet implemented")
    }

    override fun getArtifactsPaths(): String? {
        TODO("Not yet implemented")
    }

    override fun getFailBuildOnExitCode(): Boolean {
        TODO("Not yet implemented")
    }

    override fun getResolvedParameters(): ResolvedParameters {
        TODO("Not yet implemented")
    }

    override fun getRunType(): String {
        TODO("Not yet implemented")
    }

    override fun getUnresolvedParameters(): UnresolvedParameters {
        TODO("Not yet implemented")
    }

    override fun getBuildParameters(): BuildParametersMap {
        TODO("Not yet implemented")
    }

    override fun getRunnerParameters(): MutableMap<String, String> {
        TODO("Not yet implemented")
    }

    override fun getBuildNumber(): String {
        TODO("Not yet implemented")
    }

    override fun getSharedConfigParameters(): MutableMap<String, String> {
        TODO("Not yet implemented")
    }

    override fun addSharedConfigParameter(key: String, value: String) {
        TODO("Not yet implemented")
    }

    override fun addSharedSystemProperty(key: String, value: String) {
        TODO("Not yet implemented")
    }

    override fun addSharedEnvironmentVariable(key: String, value: String) {
        TODO("Not yet implemented")
    }

    override fun getSharedBuildParameters(): BuildParametersMap {
        TODO("Not yet implemented")
    }

    override fun getSharedParametersResolver(): ValueResolver {
        TODO("Not yet implemented")
    }

    override fun getBuildFeatures(): MutableCollection<AgentBuildFeature> {
        TODO("Not yet implemented")
    }

    override fun getBuildFeaturesOfType(type: String): MutableCollection<AgentBuildFeature> {
        TODO("Not yet implemented")
    }

    override fun stopBuild(reason: String) {
        TODO("Not yet implemented")
    }

    override fun getInterruptReason(): BuildInterruptReason? {
        TODO("Not yet implemented")
    }

    override fun interruptBuild(comment: String, reQueue: Boolean) {
        TODO("Not yet implemented")
    }

    override fun isBuildFailingOnServer(): Boolean {
        TODO("Not yet implemented")
    }

    override fun isInAlwaysExecutingStage(): Boolean {
        TODO("Not yet implemented")
    }

    override fun getPasswordReplacer(): PasswordReplacer {
        TODO("Not yet implemented")
    }

    override fun getArtifactStorageSettings(): MutableMap<String, String> {
        TODO("Not yet implemented")
    }
}