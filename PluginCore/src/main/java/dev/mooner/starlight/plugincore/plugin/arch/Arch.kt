/*
 * Arch.kt created by Minki Moon(mooner1022) on 23. 3. 4. 오후 6:23
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.plugincore.plugin.arch

enum class Arch(
    vararg val archNames: String
) {
    X86_64("x86_64", "x8664", "amd64", "ia32e", "em64t", "x64"),
    X86_32("x86_32", "x8632", "x86", "i386", "i486", "i586", "i686", "ia32", "x32"),
    ARM_32("arm", "arm32"),
    AARCH_64("aarch64"),
    RISCV("riscv", "riscv32"),
    RISCV_64("riscv64"),
    UNKNOWN
}

fun getArch(): Arch =
    System.getProperty("os.arch")?.let { getArchByName(it) } ?: Arch.UNKNOWN

fun getArchByName(name: String): Arch =
    Arch.values().firstOrNull { name in it.archNames } ?: Arch.UNKNOWN