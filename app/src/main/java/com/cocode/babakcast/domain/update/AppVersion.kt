package com.cocode.babakcast.domain.update

data class AppVersion(
    val major: Int,
    val minor: Int,
    val patch: Int
) : Comparable<AppVersion> {

    override fun compareTo(other: AppVersion): Int {
        if (major != other.major) return major.compareTo(other.major)
        if (minor != other.minor) return minor.compareTo(other.minor)
        return patch.compareTo(other.patch)
    }

    companion object {
        private val PATTERN = Regex("""^[vV]?(\d+)\.(\d+)(?:\.(\d+))?$""")

        fun parse(text: String): AppVersion? {
            val match = PATTERN.matchEntire(text.trim()) ?: return null
            val (majorStr, minorStr, patchStr) = match.destructured
            return AppVersion(
                major = majorStr.toInt(),
                minor = minorStr.toInt(),
                patch = patchStr.ifEmpty { "0" }.toInt()
            )
        }
    }
}
