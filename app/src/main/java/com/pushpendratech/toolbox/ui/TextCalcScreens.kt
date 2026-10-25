package com.pushpendratech.toolbox.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.pushpendratech.toolbox.core.Calculators
import com.pushpendratech.toolbox.core.TextTools
import com.pushpendratech.toolbox.data.Tool
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
private fun TextInput(value: String, onChange: (String) -> Unit, label: String = "Your text") {
    OutlinedTextField(value, onChange, label = { Text(label) }, modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp), minLines = 4)
}
@Composable
private fun CopyButton(text: String) {
    val clipboard = LocalClipboardManager.current
    OutlinedButton(onClick = { clipboard.setText(AnnotatedString(text)) }, modifier = Modifier.fillMaxWidth()) { Text("Copy result") }
}
@Composable
private fun OutputBox(text: String) {
    if (text.isNotEmpty()) Card(Modifier.fillMaxWidth()) { Text(text, Modifier.padding(12.dp)) }
}
@Composable
private fun NumField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(value, { onChange(it.filter { c -> c.isDigit() || c == '.' || c == '-' }) }, label = { Text(label) },
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(), singleLine = true)
}
private fun fmt(v: Double): String = if (v == v.toLong().toDouble()) String.format("%,.0f", v) else String.format("%,.2f", v)

/** Typing/pasting digits for the Age Calculator → progressive dd-MM-yyyy mask:
 *  "0" → "0", "04" → "04", "040" → "04-0", "0402" → "04-02", "04021" → "04-02-1",
 *  "04021994" → "04-02-1994". Non-digits are dropped; max 8 digits. */
private fun formatDobInput(raw: String): String {
    val digits = raw.filter { it.isDigit() }.take(8)
    return buildString {
        digits.forEachIndexed { i, c ->
            if (i == 2 || i == 4) append('-')
            append(c)
        }
    }
}

@Composable
fun TextToolScreen(tool: Tool, isFav: Boolean, onToggleFav: () -> Unit, onBack: () -> Unit) {
    ToolScaffold(tool, isFav, onToggleFav, onBack) {
        var input by remember { mutableStateOf("") }
        var output by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }
        when (tool.id) {
            "word_counter", "char_counter" -> {
                TextInput(input, { input = it })
                val c = remember(input) { TextTools.count(input) }
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (tool.id == "word_counter") {
                        Text("Words: ${c.words}"); Text("Characters: ${c.charsWithSpaces}"); Text("Lines: ${c.lines}")
                        Text("Sentences: ${c.sentences}"); Text("Paragraphs: ${c.paragraphs}")
                        Text("Reading time: ~${String.format("%.1f", c.readingMinutes)} min (at 200 words/min)")
                    } else {
                        Text("Characters (with spaces & line breaks): ${c.charsWithSpaces}")
                        Text("Characters (without any whitespace): ${c.charsNoSpaces}")
                        Text("Counting rule: every character counts once, including spaces, tabs and new lines in the first number. Words: ${c.words}")
                    }
                } }
            }
            "case_converter" -> {
                TextInput(input, { input = it })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { output = TextTools.toUpper(input) }) { Text("UPPER") }
                    OutlinedButton(onClick = { output = TextTools.toLower(input) }) { Text("lower") }
                    OutlinedButton(onClick = { output = TextTools.toTitleCase(input) }) { Text("Title") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { output = TextTools.toSentenceCase(input) }) { Text("Sentence") }
                    OutlinedButton(onClick = { output = TextTools.toAlternating(input) }) { Text("aLtErNaTiNg") }
                }
                OutputBox(output); if (output.isNotEmpty()) CopyButton(output)
            }
            "dedup_lines" -> {
                var ignoreCase by remember { mutableStateOf(false) }
                TextInput(input, { input = it })
                Row { Checkbox(ignoreCase, { ignoreCase = it }); Text("Ignore case when comparing", Modifier.padding(top = 12.dp)) }
                Button(onClick = { output = TextTools.removeDuplicateLines(input, ignoreCase) }, modifier = Modifier.fillMaxWidth()) { Text("Remove duplicates") }
                Text("Keeps the first occurrence of each line, in original order.", style = MaterialTheme.typography.bodySmall)
                OutputBox(output); if (output.isNotEmpty()) CopyButton(output)
            }
            "remove_spaces" -> {
                var collapse by remember { mutableStateOf(true) }; var dropBlank by remember { mutableStateOf(false) }
                TextInput(input, { input = it })
                Row { Checkbox(collapse, { collapse = it }); Text("Collapse repeated spaces inside lines", Modifier.padding(top = 12.dp)) }
                Row { Checkbox(dropBlank, { dropBlank = it }); Text("Remove blank lines", Modifier.padding(top = 12.dp)) }
                Button(onClick = { output = TextTools.removeExtraSpaces(input, collapse, dropBlank) }, modifier = Modifier.fillMaxWidth()) { Text("Clean spaces") }
                OutputBox(output); if (output.isNotEmpty()) CopyButton(output)
            }
            "text_reverser" -> {
                TextInput(input, { input = it })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { output = TextTools.reverseCharacters(input) }) { Text("Reverse characters") }
                    OutlinedButton(onClick = { output = TextTools.reverseLines(input) }) { Text("Reverse lines") }
                    OutlinedButton(onClick = { output = TextTools.reverseWords(input) }) { Text("Reverse words") }
                }
                OutputBox(output); if (output.isNotEmpty()) CopyButton(output)
            }
            "text_slug" -> {
                TextInput(input, { input = it }, "Title / heading text")
                Button(onClick = { output = TextTools.toSlug(input) }, modifier = Modifier.fillMaxWidth()) { Text("Make slug") }
                Text("Rule: accents are stripped (é → e), everything except a–z and 0–9 becomes one hyphen. Non-Latin scripts are dropped.", style = MaterialTheme.typography.bodySmall)
                OutputBox(output); if (output.isNotEmpty()) CopyButton(output)
            }
            "find_replace" -> {
                var find by remember { mutableStateOf("") }; var repl by remember { mutableStateOf("") }
                var ignoreCase by remember { mutableStateOf(false) }; var wholeWord by remember { mutableStateOf(false) }
                TextInput(input, { input = it })
                OutlinedTextField(find, { find = it }, label = { Text("Find") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(repl, { repl = it }, label = { Text("Replace with") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Row { Checkbox(ignoreCase, { ignoreCase = it }); Text("Ignore case", Modifier.padding(top = 12.dp)) }
                Row { Checkbox(wholeWord, { wholeWord = it }); Text("Whole words only", Modifier.padding(top = 12.dp)) }
                Button(onClick = {
                    error = null
                    try { val r = TextTools.findReplace(input, find, repl, ignoreCase, wholeWord); output = r.text; error = "Replaced ${r.replacements} occurrence(s). Review before copying." }
                    catch (e: Exception) { error = e.message }
                }, enabled = find.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text("Replace all") }
                error?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                OutputBox(output); if (output.isNotEmpty()) CopyButton(output)
            }
            else -> Text("Unknown text tool: ${tool.id}")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculatorScreen(tool: Tool, isFav: Boolean, onToggleFav: () -> Unit, onBack: () -> Unit) {
    ToolScaffold(tool, isFav, onToggleFav, onBack) {
        var result by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }
        fun run(block: () -> String) { error = null; result = try { block() } catch (e: Exception) { error = e.message ?: "Invalid input"; "" } }
        when (tool.id) {
            "calc_percentage" -> {
                var mode by remember { mutableStateOf(0) }; var a by remember { mutableStateOf("") }; var b by remember { mutableStateOf("") }
                val modes = listOf("% of a number", "X is what % of Y", "% change", "% difference")
                modes.forEachIndexed { i, label -> Row { RadioButton(mode == i, { mode = i }); Text(label, Modifier.padding(top = 12.dp)) } }
                NumField(if (mode == 0) "Percent" else "First value", a) { a = it }
                NumField(if (mode == 0) "Of number" else "Second value", b) { b = it }
                Button(onClick = { run {
                    val x = a.toDouble(); val y = b.toDouble()
                    when (mode) {
                        0 -> "${fmt(x)}% of ${fmt(y)} = ${fmt(Calculators.percentOf(x, y))}"
                        1 -> "${fmt(x)} is ${fmt(Calculators.whatPercent(x, y))}% of ${fmt(y)}"
                        2 -> "Change from ${fmt(x)} to ${fmt(y)} = ${fmt(Calculators.percentChange(x, y))}%"
                        else -> "Difference between ${fmt(x)} and ${fmt(y)} = ${fmt(Calculators.percentDifference(x, y))}%"
                    }
                } }, modifier = Modifier.fillMaxWidth()) { Text("Calculate") }
            }
            "calc_gst" -> {
                var amount by remember { mutableStateOf("") }; var rate by remember { mutableStateOf(18.0) }; var addMode by remember { mutableStateOf(true) }
                NumField("Amount (₹)", amount) { amount = it }
                Text("GST rate: ${rate}%")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Calculators.gstRates.forEach { r -> FilterChip(rate == r, { rate = r }, label = { Text("${r}%") }) }
                }
                Row { RadioButton(addMode, { addMode = true }); Text("Add GST", Modifier.padding(top = 12.dp)); RadioButton(!addMode, { addMode = false }); Text("Remove GST (price includes GST)", Modifier.padding(top = 12.dp)) }
                Button(onClick = { run {
                    val v = amount.toDouble(); val r = if (addMode) Calculators.gstAdd(v, rate) else Calculators.gstRemove(v, rate)
                    "Taxable value: ₹${fmt(r.taxable)}\nGST (${rate}%): ₹${fmt(r.gstAmount)}  (CGST ₹${fmt(r.cgst)} + SGST ₹${fmt(r.sgst)})\nTotal: ₹${fmt(r.total)}"
                } }, modifier = Modifier.fillMaxWidth()) { Text("Calculate GST") }
                NoteCard("This is a calculation only, not tax or legal advice. Rates change — verify the current GST rate for your item.")
            }
            "calc_emi" -> {
                var p by remember { mutableStateOf("") }; var rate by remember { mutableStateOf("") }; var years by remember { mutableStateOf("") }; var monthsExtra by remember { mutableStateOf("") }
                NumField("Loan amount (₹)", p) { p = it }; NumField("Annual interest rate (%)", rate) { rate = it }
                NumField("Tenure (years)", years) { years = it }; NumField("Extra months (optional)", monthsExtra) { monthsExtra = it }
                Button(onClick = { run {
                    val totalMonths = (years.toDoubleOrNull() ?: 0.0).toInt() * 12 + (monthsExtra.toIntOrNull() ?: 0)
                    val r = Calculators.emi(p.toDouble(), rate.toDouble(), totalMonths)
                    "Monthly EMI: ₹${fmt(r.emi)}\nTotal payment: ₹${fmt(r.totalPayment)}\nTotal interest: ₹${fmt(r.totalInterest)}\nAssumes a fixed rate, monthly payments, and no processing fees or prepayments."
                } }, modifier = Modifier.fillMaxWidth()) { Text("Calculate EMI") }
            }
            "calc_discount" -> {
                var price by remember { mutableStateOf("") }; var d1 by remember { mutableStateOf("") }; var d2 by remember { mutableStateOf("") }
                NumField("Original price (₹)", price) { price = it }; NumField("Discount %", d1) { d1 = it }; NumField("Extra discount % (optional, applied after the first)", d2) { d2 = it }
                Button(onClick = { run {
                    val list = listOfNotNull(d1.toDoubleOrNull(), d2.toDoubleOrNull())
                    val r = Calculators.discount(price.toDouble(), list)
                    "You save: ₹${fmt(r.discountAmount)} (${fmt(r.effectivePercent)}% total)\nFinal price: ₹${fmt(r.finalPrice)}"
                } }, modifier = Modifier.fillMaxWidth()) { Text("Calculate discount") }
            }
            "calc_age" -> {
                var dobText by remember { mutableStateOf("") }
                var showPicker by remember { mutableStateOf(false) }
                // v1.4.4 (Pushpendra's ask): dashes insert themselves while typing —
                // type 04 → "04-", then 02 → "04-02-", then the year. Number pad too.
                OutlinedTextField(
                    dobText, { dobText = formatDobInput(it) },
                    label = { Text("Date of birth (dd-MM-yyyy)") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                )
                OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth()) { Text("Pick date") }
                if (showPicker) {
                    val picker = rememberDatePickerState()
                    DatePickerDialog(onDismissRequest = { showPicker = false }, confirmButton = {
                        TextButton(onClick = {
                            picker.selectedDateMillis?.let { ms -> dobText = LocalDate.ofEpochDay(ms / 86_400_000).format(DateTimeFormatter.ofPattern("dd-MM-yyyy")) }
                            showPicker = false
                        }) { Text("OK") }
                    }) { DatePicker(picker) }
                }
                Button(onClick = { run {
                    // Friendly errors only — the raw java.time parse exception used to leak
                    // onto this screen ("Text '04021994' could not be parsed at index 2").
                    require(dobText.count { it.isDigit() } == 8) { "Enter the full date — it fills in as dd-MM-yyyy while you type (for example 04-02-1994)." }
                    val dob = runCatching {
                        LocalDate.parse(dobText.trim(), DateTimeFormatter.ofPattern("dd-MM-uuuu").withResolverStyle(java.time.format.ResolverStyle.STRICT))
                    }.getOrElse { throw IllegalArgumentException("That isn't a real calendar date — check the day and month (dd-MM-yyyy).") }
                    val r = Calculators.age(dob)
                    "Age: ${r.years} years, ${r.months} months, ${r.days} days\nTotal days lived: ${r.totalDays} (${r.totalWeeks} weeks)\nYour date of birth stays on this device — it is never sent anywhere."
                } }, modifier = Modifier.fillMaxWidth()) { Text("Calculate age") }
            }
            "calc_units" -> {
                var family by remember { mutableStateOf(Calculators.UnitFamily.LENGTH) }
                var value by remember { mutableStateOf("") }
                val familyUnits = remember(family) { Calculators.units.filter { it.family == family } }
                var from by remember(family) { mutableStateOf(familyUnits.first()) }
                var to by remember(family) { mutableStateOf(familyUnits.getOrElse(1) { familyUnits.first() }) }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Calculators.UnitFamily.entries.forEach { f -> FilterChip(family == f, { family = f }, label = { Text(f.name.lowercase().replaceFirstChar { c -> c.uppercase() }) }) }
                }
                NumField("Value", value) { value = it }
                UnitDropdown("From", familyUnits, from) { from = it }
                UnitDropdown("To", familyUnits, to) { to = it }
                Button(onClick = { run { "${fmt(value.toDouble())} ${from.symbol} = ${fmt(Calculators.convert(value.toDouble(), from, to))} ${to.symbol}" } }, modifier = Modifier.fillMaxWidth()) { Text("Convert") }
            }
            else -> Text("Unknown calculator: ${tool.id}")
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (result.isNotEmpty()) Card(Modifier.fillMaxWidth()) { Text(result, Modifier.padding(12.dp)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnitDropdown(label: String, units: List<Calculators.UnitDef>, selected: Calculators.UnitDef, onSelect: (Calculators.UnitDef) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded, { expanded = it }) {
        OutlinedTextField("${selected.label} (${selected.symbol})", {}, readOnly = true, label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }, modifier = Modifier.fillMaxWidth().menuAnchor())
        ExposedDropdownMenu(expanded, { expanded = false }) {
            units.forEach { u -> DropdownMenuItem(text = { Text("${u.label} (${u.symbol})") }, onClick = { onSelect(u); expanded = false }) }
        }
    }
}
