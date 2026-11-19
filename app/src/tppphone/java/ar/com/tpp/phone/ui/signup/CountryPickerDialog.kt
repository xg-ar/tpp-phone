// SPDX-License-Identifier: GPL-3.0-or-later
package ar.com.tpp.phone.ui.signup

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import androidx.appcompat.app.AlertDialog
import ar.com.tpp.phone.logic.Country
import java.text.Normalizer
import org.linphone.R

/** Flag + name + calling code, searchable by name (accents ignored). */
object CountryPickerDialog {
    fun label(country: Country) = "${country.flag}  ${country.name}  +${country.callingCode}"

    fun show(context: Context, countries: List<Country>, onPick: (Country) -> Unit) {
        val density = context.resources.displayMetrics.density
        val search = EditText(context).apply {
            hint = context.getString(R.string.tpp_number_country_search)
            isSingleLine = true
        }
        var shown = countries
        val adapter = ArrayAdapter(context, android.R.layout.simple_list_item_1, shown.map(::label).toMutableList())
        val list = ListView(context).apply { this.adapter = adapter }
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (16 * density).toInt()
            setPadding(pad, pad / 2, pad, 0)
            addView(search)
            addView(list, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (400 * density).toInt()))
        }
        val dialog = AlertDialog.Builder(context)
            .setTitle(R.string.tpp_number_country)
            .setView(content)
            .setNegativeButton(R.string.tpp_cancel, null)
            .create()
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                val q = fold(s.toString())
                shown = countries.filter { fold(it.name).contains(q) }
                adapter.clear()
                adapter.addAll(shown.map(::label))
            }
        })
        list.setOnItemClickListener { _, _, position, _ ->
            onPick(shown[position])
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun fold(s: String) =
        Normalizer.normalize(s.trim().lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")
}
