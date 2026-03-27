package io.ysakhno.tools.spotlight.imagedownloader

import com.github.ajalt.clikt.core.main
import io.ysakhno.tools.spotlight.imagedownloader.commands.Root

/**
 * The application's main entry point.
 *
 * @param args an array containing the command-line arguments.
 */
fun main(args: Array<String>) = Root().main(args)
