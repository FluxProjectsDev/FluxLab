# Kebijakan Privasi

Terakhir ditinjau: FluxLab 0.1.0

FluxLab adalah perangkat lunak diagnostik dan benchmark yang mengutamakan penyimpanan lokal. FluxLab mengumpulkan telemetri perangkat hanya untuk menampilkan pemantauan dan menghitung hasil benchmark yang diminta pengguna.

## Data yang disimpan di perangkat

- Sampel langsung berada di memori yang dibatasi dan tidak diunggah oleh FluxLab.
- Sesi benchmark dan preferensi aplikasi disimpan di sandbox aplikasi melalui Room dan DataStore.
- Identitas perangkat keras, informasi build Android, kondisi termal, kondisi baterai, dan peringatan pengukuran dapat disimpan dalam sesi karena diperlukan untuk memahami keterulangan hasil.

## Akses root dan sistem

Akses root bersifat opsional. FluxLab hanya menggunakannya untuk pembacaan diagnostik yang diizinkan. Kemampuan yang ditolak atau tidak didukung tetap ditampilkan sebagai tidak tersedia dan tidak menghalangi pengukuran non-root.

## Ekspor dan berbagi

Laporan hanya ditulis setelah pengguna memilih tujuan melalui pemilih dokumen Android. Berbagi dimulai hanya melalui tindakan pengguna yang jelas. Berkas yang diekspor berada di bawah kendali penyedia tujuan dan tidak dihapus saat FluxLab dicopot.

## Jaringan dan analitik

Aplikasi saat ini tidak meminta akses jaringan dan tidak menyertakan analitik, iklan, unggah crash, atau unggah telemetri diam-diam. Sumber update masa depan harus dikonfigurasi dan didokumentasikan secara eksplisit sebelum pemeriksaan update diaktifkan.

## Penyimpanan dan penghapusan

Mencopot FluxLab menghapus database dan preferensi di sandbox sesuai perilaku pencadangan Android. Halaman Pengaturan dapat menghapus riwayat benchmark. Berkas yang diekspor ke luar sandbox harus dikelola di tujuan tersebut.

## Kontak dan perubahan

Kebijakan ini dipelihara bersama sumber FluxLab. Perubahan material akan dijelaskan dalam changelog yang didistribusikan bersama versi aplikasi terkait.
