package com.example.memoaese_;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class CreateNoteActivity extends AppCompatActivity {

    private EditText editTextJudul, editTextKonten;
    private Button buttonHapus, buttonSimpanLayout;
    private ImageButton buttonBack;

    private boolean isEditMode = false;
    private long noteId = -1;
    private DatabaseHelper dbHelper;

    private static final String PREFS_NAME = "MemoDraft";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_note);

        // --- 1. PENGATURAN STATUS BAR ---
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // --- 2. INISIALISASI DATABASE & UI ---
        dbHelper = new DatabaseHelper(this);

        editTextJudul = findViewById(R.id.edit_text_judul);
        editTextKonten = findViewById(R.id.edit_text_konten);
        buttonHapus = findViewById(R.id.button_hapus);
        buttonSimpanLayout = findViewById(R.id.button_simpan);
        buttonBack = findViewById(R.id.button_back_create);

        if (editTextJudul == null || editTextKonten == null) {
            Toast.makeText(this, "Error: Komponen UI tidak ditemukan!", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // --- 3. LOGIKA KLIK TOMBOL ---

        if (buttonSimpanLayout != null) {
            buttonSimpanLayout.setOnClickListener(v -> saveNotePermanently());
        }

        if (buttonBack != null) {
            buttonBack.setOnClickListener(v -> onBackPressed());
        }

        // Jalankan logika pengecekan Mode Edit atau Mode Baru
        handleIntent();

        if (buttonHapus != null) {
            buttonHapus.setOnClickListener(v -> showDeleteConfirmationDialog());
        }
    }

    private void handleIntent() {
        Intent intent = getIntent();
        if (intent != null && intent.hasExtra("NOTE_ID")) {
            // MODE EDIT: Mengambil data dari database
            isEditMode = true;
            noteId = intent.getLongExtra("NOTE_ID", -1);
            setTitle("Edit Catatan");
            if (buttonHapus != null) buttonHapus.setVisibility(View.VISIBLE);
            loadNoteDataFromDatabase();
        } else {
            // MODE BARU: Pastikan halaman kosong
            isEditMode = false;
            setTitle("Buat Catatan Baru");
            if (buttonHapus != null) buttonHapus.setVisibility(View.GONE);

            // Kosongkan EditText secara manual untuk memastikan tidak ada teks sisa
            editTextJudul.setText("");
            editTextKonten.setText("");

            // Muat draft hanya jika SharedPreferences tidak kosong (dihandle oleh loadDraft)
            loadDraft();
        }
    }

    private void loadNoteDataFromDatabase() {
        for (Note note : dbHelper.getAllNotes()) {
            if (note.getId() == noteId) {
                editTextJudul.setText(note.getTitle());
                editTextKonten.setText(note.getContent());
                break;
            }
        }
    }

    private void saveNotePermanently() {
        String judul = editTextJudul.getText().toString().trim();
        String konten = editTextKonten.getText().toString().trim();

        if (judul.isEmpty()) {
            Toast.makeText(this, "Judul tidak boleh kosong", Toast.LENGTH_SHORT).show();
            return;
        }

        if (isEditMode) {
            dbHelper.updateNote(noteId, judul, konten);
            Toast.makeText(this, "Catatan diperbarui!", Toast.LENGTH_SHORT).show();
        } else {
            dbHelper.insertNote(judul, konten);
            Toast.makeText(this, "Catatan disimpan!", Toast.LENGTH_SHORT).show();
            // Hapus draft setelah berhasil disimpan secara permanen
            clearDraft();
        }
        finish();
    }

    // --- FITUR AUTO-SAVE DRAFT (Hanya bekerja saat mengetik catatan baru) ---
    @Override
    protected void onPause() {
        super.onPause();
        // Simpan ke SharedPreferences jika pengguna keluar sebelum menekan tombol simpan
        if (!isEditMode) {
            saveDraft();
        }
    }

    private void saveDraft() {
        String judul = editTextJudul.getText().toString();
        String konten = editTextKonten.getText().toString();

        // Jangan simpan jika keduanya kosong
        if (judul.isEmpty() && konten.isEmpty()) return;

        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .putString("draft_judul", judul)
                .putString("draft_konten", konten)
                .apply();
    }

    private void loadDraft() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String draftJudul = prefs.getString("draft_judul", "");
        String draftKonten = prefs.getString("draft_konten", "");

        // Hanya set teks jika draft memang ada isinya
        if (!draftJudul.isEmpty() || !draftKonten.isEmpty()) {
            editTextJudul.setText(draftJudul);
            editTextKonten.setText(draftKonten);
        }
    }

    private void clearDraft() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().clear().apply();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_create_note, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_save) {
            saveNotePermanently();
            return true;
        } else if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showDeleteConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Hapus Catatan")
                .setMessage("Yakin ingin menghapus catatan ini?")
                .setPositiveButton("Hapus", (dialog, which) -> {
                    if (isEditMode) dbHelper.deleteNote(noteId);
                    clearDraft();
                    Toast.makeText(this, "Catatan dihapus!", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .setNegativeButton("Batal", null)
                .show();
    }
}