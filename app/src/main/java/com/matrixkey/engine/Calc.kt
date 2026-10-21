package com.matrixkey.engine

import java.math.BigDecimal
import java.math.MathContext
import kotlin.math.abs
import kotlin.math.pow

/**
 * Solves the arithmetic right before an "=" you type: "5+5=" → "10",
 * "5+5(6/2)=" → "20". Supports + - * / × ÷ ^ %, parentheses, implied
 * multiplication ("2(3)", "(1+1)(2)"), decimals and thousands commas.
 */
object Calc {

    private const val OPS = "+-*/×÷^%−–·"
    private val allowed = "0123456789.,() " + OPS

    /**
     * [before] is the text before the "=" (not including it). Returns the answer
     * to insert, or null when there's no math problem there.
     */
    fun solveBefore(before: String): String? {
        val tail = before.takeLastWhile { it in allowed }.trim()
        if (tail.isEmpty() || !tail.any { it.isDigit() }) return null
        // Needs an actual operation, not just a number like "2026=".
        val body = tail.trimStart('-', '−', '–', '+')
        if (!body.any { it in OPS || it == '(' }) return null
        // Try the longest tail first, dropping leading junk (e.g. a stray ")").
        for (start in tail.indices) {
            if (!tail[start].isDigit() && tail[start] !in "(.-−–") continue
            val v = runCatching { Parser(tail.substring(start)).parseAll() }.getOrNull() ?: continue
            if (v.isNaN() || v.isInfinite()) return null
            return format(v)
        }
        return null
    }

    fun format(v: Double): String {
        if (v == 0.0) return "0"
        if (abs(v) >= 1e15 || abs(v) < 1e-9) return BigDecimal(v).round(MathContext(10)).stripTrailingZeros().toString()
        val bd = BigDecimal(v).round(MathContext(12)).stripTrailingZeros()
        return bd.toPlainString()
    }

    private class Parser(src: String) {
        // Normalise: thousands commas ("1,000"), and the various operator glyphs.
        private val s = src
            .replace(Regex("(?<=\\d),(?=\\d{3}(\\D|$))"), "")
            .replace('×', '*').replace('·', '*').replace('÷', '/')
            .replace('−', '-').replace('–', '-')
            .replace(" ", "")
        private var i = 0

        fun parseAll(): Double {
            val v = expr()
            if (i != s.length) throw IllegalArgumentException("junk at $i")
            return v
        }

        private fun peek() = if (i < s.length) s[i] else '\u0000'

        private fun expr(): Double {
            var v = term()
            while (true) {
                v = when (peek()) {
                    '+' -> { i++; v + term() }
                    '-' -> { i++; v - term() }
                    else -> return v
                }
            }
        }

        private fun term(): Double {
            var v = power()
            while (true) {
                val c = peek()
                v = when {
                    c == '*' -> { i++; v * power() }
                    c == '/' -> { i++; val d = power(); if (d == 0.0) throw ArithmeticException(); v / d }
                    // Implied multiplication: 5(6/2), (1+1)(2), (2)3
                    c == '(' || (c.isDigit() && i > 0 && s[i - 1] == ')') -> v * power()
                    else -> return v
                }
            }
        }

        private fun power(): Double {
            val base = unary()
            if (peek() == '^') { i++; return base.pow(power()) }
            return base
        }

        private fun unary(): Double = when (peek()) {
            '-' -> { i++; -unary() }
            '+' -> { i++; unary() }
            else -> postfix()
        }

        private fun postfix(): Double {
            var v = primary()
            while (peek() == '%') { i++; v /= 100.0 }
            return v
        }

        private fun primary(): Double {
            if (peek() == '(') {
                i++
                val v = expr()
                if (peek() == ')') i++ // a missing ")" at the end is forgiven
                else if (i < s.length) throw IllegalArgumentException("expected )")
                return v
            }
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
            if (start == i) throw IllegalArgumentException("number expected at $i")
            return s.substring(start, i).toDouble()
        }
    }
}
