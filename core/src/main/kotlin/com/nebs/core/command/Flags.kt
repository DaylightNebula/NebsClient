package com.nebs.core.command

/**
 * Minimal `--flag value` / `--flag=value` / `--switch` parser shared by command front ends.
 *
 * @property values flags that took a value.
 * @property switches boolean flags that were present.
 * @property positional everything that wasn't a flag.
 */
class Flags private constructor(
    val values: Map<String, List<String>>,
    val switches: Set<String>,
    val positional: List<String>,
) {
    operator fun get(flag: String): String? = values[flag]?.last()

    fun all(flag: String): List<String> = values[flag].orEmpty()

    operator fun contains(flag: String): Boolean = flag in switches

    companion object {
        /**
         * @param valueFlags flags (without `--`) that take a value.
         * @param switchFlags flags (without `--`) that don't.
         * @throws CommandException on an unknown flag or a missing value.
         */
        fun parse(args: List<String>, valueFlags: Set<String>, switchFlags: Set<String> = emptySet()): Flags {
            val values = mutableMapOf<String, MutableList<String>>()
            val switches = mutableSetOf<String>()
            val positional = mutableListOf<String>()
            val iterator = args.iterator()
            while (iterator.hasNext()) {
                val arg = iterator.next()
                if (!arg.startsWith("--")) {
                    positional += arg
                    continue
                }
                val name = arg.removePrefix("--").substringBefore('=')
                val inline = if ('=' in arg) arg.substringAfter('=') else null
                when (name) {
                    in valueFlags -> {
                        val value = inline ?: if (iterator.hasNext()) iterator.next() else throw CommandException("--$name requires a value")
                        values.getOrPut(name) { mutableListOf() } += value
                    }
                    in switchFlags -> switches += name
                    else -> throw CommandException("unknown option --$name")
                }
            }
            return Flags(values, switches, positional)
        }
    }
}
