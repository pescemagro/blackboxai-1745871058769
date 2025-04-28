package com.example.paddleocrandroidapp

import android.app.Activity
import android.app.ProgressDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform

class MainActivity : AppCompatActivity() {

    private val PICK_PDF_FILE = 2
    private lateinit var progressDialog: ProgressDialog

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }

        // Start file picker immediately on launch
        pickPdfFile()
    }

    private fun pickPdfFile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/pdf"
        }
        startActivityForResult(intent, PICK_PDF_FILE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == PICK_PDF_FILE && resultCode == Activity.RESULT_OK) {
            data?.data?.also { uri ->
                val fileName = getFileName(uri)
                if (fileName != null) {
                    processPdf(uri, fileName)
                } else {
                    showErrorDialog("Failed to get file name")
                }
            }
        } else {
            finish() // Close app if no file selected
        }
    }

    private fun getFileName(uri: Uri): String? {
        var name: String? = null
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst()) {
                name = cursor.getString(nameIndex)
            }
        }
        return name
    }

    private fun processPdf(uri: Uri, fileName: String) {
        progressDialog = ProgressDialog(this).apply {
            setMessage("Processing PDF...")
            setCancelable(false)
            isIndeterminate = true
            show()
        }

        Thread {
            try {
                val inputStream = contentResolver.openInputStream(uri)
                val tempFile = createTempFile(suffix = ".pdf")
                inputStream?.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                val py = Python.getInstance()
                val module = py.getModule("ocr_script")
                val outputPath = tempFile.parent + "/" + fileName.replace(".pdf", "_restored.pdf")

                val result: PyObject = module.callAttr("process_pdf", tempFile.absolutePath, outputPath)

                runOnUiThread {
                    progressDialog.dismiss()
                    if (result.toBoolean()) {
                        showInfoDialog("Created:\n${fileName.replace(".pdf", "_restored.pdf")}")
                    } else {
                        showErrorDialog("Failed to process PDF")
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    progressDialog.dismiss()
                    showErrorDialog("Error: ${e.message}")
                }
            }
        }.start()
    }

    private fun showInfoDialog(message: String) {
        AlertDialog.Builder(this)
            .setTitle("Info")
            .setMessage(message)
            .setPositiveButton("OK") { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }

    private fun showErrorDialog(message: String) {
        AlertDialog.Builder(this)
            .setTitle("Error")
            .setMessage(message)
            .setPositiveButton("OK") { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }
}
