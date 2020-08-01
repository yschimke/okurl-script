package com.baulsupp.okscript

import org.slf4j.jul.JDK14LoggerFactory
import java.util.logging.Formatter
import java.util.logging.Level
import java.util.logging.LogManager
import java.util.logging.LogRecord
import java.util.logging.Logger

val activeLoggers = mutableListOf<Logger>()

fun getLogger(name: String): Logger {
  val logger = Logger.getLogger(name)
  activeLoggers.add(logger)
  return logger
}

fun initLogging() {
  LogManager.getLogManager().reset()

  val activeLogger = getLogger("")
  val handler = java.util.logging.ConsoleHandler()
  handler.level = Level.ALL
  handler.formatter = object : Formatter() {
    override fun format(record: LogRecord?): String = record?.message ?: ""
  }
  activeLogger.addHandler(handler)

  getLogger("").level = Level.SEVERE
}
