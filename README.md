Panel aplikasi melayang minimalis di tepi layar Android. Buka aplikasi favorit tanpa harus kembali ke Home atau berpindah dari aplikasi yang sedang digunakan.

Dibuat oleh **Nexora**.

## Fitur

- **Navigasi 4 tab:** Utama, Panel, Aplikasi, dan Bantuan.
- **Desain putih-hitam:** antarmuka utama menggunakan palet monokrom dan tidak mengikuti Dark Mode sistem.
- **Panel tepi:** garis `|` berada di sisi layar. Ketuk atau geser ke arah dalam untuk membuka panel.
- **Pindah sisi:** grip `--` di bagian atas panel dapat digeser ke kiri atau kanan.
- **Grid dua kolom:** pilih tampilan 1–3 baris. Jika aplikasi atau item lebih banyak, geser panel ke atas atau bawah.
- **Ukuran panel:** lebar dapat diatur pada rentang 100–220 dp.
- **Pengaturan garis tepi:** tinggi 10–50 dp dan posisi vertikal 0–100%.
- **Daftar tambah aplikasi:** tombol `+` menampilkan pemilih aplikasi mengambang. Lebar 44–100 dp dan jumlah ikon yang terlihat 3–7.
- **Papan klip:** hingga 12 teks terakhir disimpan dan ditampilkan sebelum tombol `+`.
- **Drag teks:** teks dari aplikasi lain dapat dijatuhkan ke panel untuk disimpan.
- **Menu aplikasi:** tekan lama ikon untuk membuka aplikasi, mengatur ukuran freeform, atau menghapus aplikasi dari panel.
- **Tutup aplikasi:** ketuk aplikasi yang sedang dibuka dalam mode freeform untuk menutupnya. Penutupan task penuh memerlukan Shizuku.
- **Mulai otomatis:** tersedia opsi menjalankan panel setelah HP menyala dan menghidupkannya kembali jika dihentikan.

## Cara menggunakan

1. Pasang dan buka Smart Panel.
2. Berikan izin **Tampil di atas aplikasi lain**. Izin ini wajib agar panel dapat muncul di atas aplikasi lain.
3. Jika Android 13 atau lebih baru meminta izin notifikasi, izinkan agar notifikasi layanan panel dapat ditampilkan.
4. Pada tab **Utama**, aktifkan Smart Panel.
5. Garis kecil akan muncul di tepi layar. Ketuk atau geser garis ke arah dalam untuk membuka panel.
6. Ketuk ikon aplikasi untuk menjalankannya. Gunakan tombol `+` untuk menambah atau melepas aplikasi dari panel.
7. Tekan grip `--` di bagian atas panel, lalu geser ke kiri atau kanan untuk memindahkan panel ke sisi lain.

## Pengaturan panel

Buka tab **Panel** untuk mengatur:

| Pengaturan | Pilihan |
|---|---|
| Sisi panel | Kiri atau kanan |
| Posisi garis | 0–100% tinggi layar |
| Tinggi garis | 10–50 dp |
| Lebar panel | 100–220 dp |
| Lebar daftar tambah (+) | 44–100 dp |
| Tinggi daftar tambah (+) | 3–7 ikon terlihat |
| Jumlah baris grid | 1, 2, atau 3 |
| Cara buka aplikasi | Auto, Penuh, atau Freeform |
| Papan klip | Aktif/nonaktif |

## Mode membuka aplikasi

- **Auto:** ketuk ikon untuk membuka layar penuh. Jika tersedia, ketuk logo freeform kecil pada ikon untuk membuka aplikasi dalam jendela mengambang.
- **Penuh:** ketuk ikon untuk selalu membuka aplikasi dalam layar penuh.
- **Freeform:** ketuk ikon untuk langsung membuka aplikasi dalam jendela mengambang. Mode ini hanya bekerja jika perangkat mendukung freeform.

Pada menu tekan lama, pilih **Atur Freeform** untuk memilih:
- **Layar penuh**
- **Standar**
- **Maksimal**
- **Potret**

Jika freeform belum tersedia, aplikasi akan dibuka dalam layar penuh atau pilihan freeform tidak dapat digunakan.

## Papan klip

Jika fitur papan klip aktif, teks yang disalin akan muncul sebagai kotak di panel sebelum tombol `+`.

- **Ketuk:** salin teks itu kembali ke papan klip.
- **Tekan lama lalu seret:** kirim teks melalui fitur drag-and-drop Android ke aplikasi tujuan.
- **Geser ke kiri atau kanan:** hapus teks dari daftar Smart Panel.
- **Seret teks dari aplikasi lain ke panel:** simpan teks tersebut ke daftar papan klip.

Smart Panel menyimpan maksimal **12 teks**. Setiap teks dibatasi hingga 2.000 karakter.

## Menutup aplikasi dan Shizuku

Android membatasi aplikasi biasa untuk menutup aplikasi lain yang masih aktif di layar. Smart Panel menyediakan penutupan aplikasi yang sedang berjalan melalui Shizuku.

1. Pasang **Shizuku** dari sumber resminya.
2. Jalankan Shizuku. Pada Android 11 ke atas, Shizuku dapat dijalankan menggunakan **Wireless debugging**. Pada Android 10 ke bawah, biasanya diperlukan komputer dan ADB.
3. Buka Smart Panel dan berikan izin Shizuku jika diminta.
4. Ketuk aplikasi yang sedang berjalan dalam mode freeform untuk memilih **Tutup**.

Tanpa akses Shizuku, Android hanya mengizinkan penghentian proses latar belakang secara terbatas. Hasilnya tidak selalu sama dengan menghapus task dari Recents.

## Mengaktifkan freeform pada perangkat yang belum mendukung

Pada beberapa perangkat, dukungan freeform dapat dicoba melalui Brevent dan Wireless debugging:

1. Pasang Brevent.
2. Aktifkan **Opsi pengembang > Debugging nirkabel** dan hubungkan Brevent.
3. Jalankan perintah berikut satu per satu melalui menu shell/perintah Brevent:

```sh
settings put global enable_freeform_support 1
settings put global force_resizable_activities 1
```

4. Restart perangkat, lalu periksa kembali status freeform di tab **Bantuan** Smart Panel.

Perintah tersebut tidak menjamin freeform berfungsi pada semua perangkat. Dukungan bergantung pada versi Android dan modifikasi sistem dari produsen. Jika tetap tidak aktif, gunakan mode **Auto** atau **Penuh**.

## Otomatis berjalan

Pada tab **Utama**, tersedia:

- **Jalan saat HP menyala:** mencoba menjalankan panel setelah perangkat selesai boot.
- **Hidupkan lagi kalau dimatikan:** mencoba memulai ulang layanan panel jika dihentikan atau dihapus dari Recents.

Pada beberapa perangkat Tecno, Infinix, itel, Xiaomi, OPPO, dan vivo, aktifkan juga izin **Autostart/Mulai otomatis** serta izinkan aplikasi berjalan di latar belakang. Penghemat baterai dari produsen dapat tetap menghentikan layanan.

## Batasan Android

- Android 10 ke atas membatasi pembacaan clipboard oleh aplikasi yang sedang berada di latar belakang. Smart Panel mencoba membaca teks dengan mengambil fokus sesaat melalui jendela sementara. Perilakunya dapat berbeda pada tiap merek dan versi Android.
- Drag-and-drop teks hanya berhasil jika aplikasi tujuan menerima data teks yang diseret. Tidak semua aplikasi mendukungnya.
- Mode freeform bergantung pada dukungan perangkat dan sistem operasi. Mengaktifkan pengaturan melalui ADB/Brevent tidak menjamin fitur tersedia.
- Izin overlay, notifikasi, autostart, dan pengelolaan baterai dapat memiliki nama atau perilaku berbeda di setiap perangkat.
- Penutupan aplikasi secara penuh membutuhkan Shizuku dan izin yang sesuai.

## Informasi teknis

| Komponen | Nilai |
|---|---|
| Nama aplikasi | Nexora Smart Panel |
| Application ID | `com.nexora.smartpanel` |
| Versi | 1.7 |
| Version code | 8 |
| Minimum Android | Android 7.0 (API 24) |
| Target Android | Android 15 (API 35) |
| Compile SDK | 35 |
| Bahasa | Kotlin |
| Java compatibility | Java 17 |
| ABI | `armeabi-v7a`, `arm64-v8a` |

Aplikasi tidak menyertakan library native buatan proyek. Dependensi utama yang digunakan adalah Hidden API Bypass dan Shizuku.

## Lisensi dan kredit

Smart Panel dikembangkan oleh **Nexora**. Periksa file lisensi repository (jika tersedia) sebelum menggunakan, memodifikasi, atau mendistribusikan ulang proyek.