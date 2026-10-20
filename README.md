# AstraxElementer 1.0.0 – Elemental RPG System (Giai đoạn 1)

Plugin nguyên tố RPG cho server **BoxMine RPG** – Paper **1.21.4**, **Java 21**, Maven.

> **Trạng thái trung thực của bản này:** mã nguồn đầy đủ cho Giai đoạn 1, nhưng **chưa được build bằng Maven và chưa chạy thử trên server thật** (môi trường soạn thảo không có mạng để tải Paper API). Xem mục *Kiểm thử* và *Giới hạn đã biết*. Bạn cần chạy `mvn clean package` và thử trên server test trước khi đưa lên server chính.

## Tính năng đã triển khai (Giai đoạn 1)

- 8 nguyên tố: Hỏa, Băng, Lôi, Thổ, Phong, Quang và hai nguyên tố **giới hạn** Bóng Tối, Rồng – màu, biểu tượng, nội tại (attribute + giảm sát thương + proc đánh thường) cấu hình trong `elements.yml`.
- **Nhiều nguyên tố, tối đa 3:** mỗi người chơi chỉ được sở hữu tối đa 3 nguyên tố (`unlock.max-owned`). Nguyên tố **đầu tiên** miễn phí (chọn trong GUI, chỉ trong 6 nguyên tố thường); **từ nguyên tố thứ hai trở đi chỉ nhận bằng Vé Nguyên Tố** (rớt từ boss hoặc đổi bằng Tinh Thể), không còn mở khóa bằng cấp/điểm. Chuyển nguyên tố đang dùng bằng GUI hoặc `/elementer switch`, có hồi chiêu chuyển (`switch.cooldown-seconds`). **Mỗi nguyên tố có cấp, EXP, điểm kỹ năng và cấp kỹ năng RIÊNG**: nguyên tố mới bắt đầu từ cấp 1, chuyển đi rồi quay lại thì tiến trình cũ còn nguyên (GUI nguyên tố hiện cấp riêng của từng nguyên tố đã có).
- **Bóng Tối (giới hạn, 4 kỹ năng):** Hắc Tiễn (xuyên, Wither, hút máu), **Kéo Hồn (kéo kẻ địch về trước mặt mình + trói ngắn)**, Vực Thẳm (mù, Wither, hút máu liên tục), Nhật Thực (tuyệt kỹ). Sát thương nền cao nhất game, khắc 5 nguyên tố cơ bản, bị Quang và Rồng khắc.
- **Rồng (giới hạn, 4 kỹ năng):** Long Tức (xuyên 4 mục tiêu), Long Phi (lao + rơi chậm), Long Uy (gầm hất văng, làm chậm/suy yếu), Long Thần Giáng Thế (tuyệt kỹ). Hào quang nhiều lớp kiểu `DRAGON` và **đôi cánh rồng bằng hạt** vỗ theo chuyển động (bật/tắt `/elementer wings`). Khắc mọi nguyên tố; không nguyên tố nào khắc Rồng.
- **Vé Nguyên Tố (tờ giấy):** nguyên tố giới hạn chỉ nhận bằng vé; **chuột phải** để sở hữu. Mỗi vé có mã duy nhất được ghi trong DB nên vé bị nhân bản chỉ dùng được một lần; vé không mất nếu bạn đã có nguyên tố đó hoặc đã đủ 3 nguyên tố. Tích hợp nạp thẻ: cho plugin cửa hàng/nạp chạy lệnh console `elementeradmin give %player% voucher dark` (hoặc `rong`).
- **Boss nguyên tố + Tinh Thể + đổi vé:** 6 boss (Chúa Tể Hỏa Ngục, Nữ Hoàng Băng Giá, Thần Sấm, Cự Thạch Thủ Hộ, Phong Vương, Thánh Kỵ Sĩ Quang Minh) dùng kỹ năng nguyên tố tấn công người chơi (có báo hiệu trước đòn vùng, tương khắc với nguyên tố người chơi, nổi điên khi còn ít máu, thanh máu). Boss chết rơi **Tinh Thể** của nguyên tố đó cho người gây ≥ 5% sát thương; đổi **48 Tinh Thể** lấy **1 Vé Nguyên Tố** bằng GUI (`/elementer exchange` hoặc nút "Đổi Tinh Thể lấy Vé" trong menu; GUI hiện cần/đang có/còn thiếu bao nhiêu nguyên liệu và boss nào rơi; **chuột phải** vào nguyên tố để xem thông tin boss: kỹ năng, lời thoại, vật phẩm rớt) hoặc lệnh `/elementer exchange <nguyên tố>`. Ngoài Tinh Thể, mỗi boss còn rơi **vật phẩm thêm** (mục `rewards.items`, roll riêng từng người) và EXP. Bóng Tối và Rồng **không** đổi được (chỉ vé nạp). Boss còn **nói trong chat** (khi xuất hiện, tung kỹ năng, còn nửa máu, nổi điên, hạ người chơi, lúc chết); lời thoại sửa trong mục `lines` của từng boss, tắt bằng `settings.speech-enabled: false`. Tất cả trong `bosses.yml`.
- **Boss huyền thoại + spawn định kỳ:** 2 boss huyền thoại **Ma Vương Hắc Ám** (Bóng Tối, có kỹ năng kéo người về phía boss) và **Long Vương Hủy Diệt** (Rồng), 3000 máu, 5 kỹ năng, nổi điên, lời thoại riêng. Chỉ xuất hiện từ bộ spawn định kỳ: đứng ở nơi muốn boss xuất hiện và gõ `/elementeradmin boss setspawn`; cứ **mỗi 2 tiếng** (`spawner.interval-minutes`) spawn 1 boss tại đó, thông báo cho **toàn server**. Tỉ lệ: **Bóng Tối 1%, Rồng 1%** (`spawn-chance` của từng boss), 98% còn lại chia đều cho 6 boss thường (`spawn-weight`). Boss huyền thoại không rơi Tinh Thể mà rớt thẳng **Vé Nguyên Tố Bóng Tối/Rồng** (`voucher-chance` 50% cho mỗi người gây đủ sát thương) cùng kim cương/ngọc lục bảo. Giờ đếm lưu trong `spawner-state.yml` nên restart không mất; boss tự biến mất sau `spawner.despawn-minutes` (mặc định 30 phút) nếu chưa bị hạ.
- **Hào quang dưới chân:** mỗi nguyên tố một hào quang riêng (hạt + kiểu RING/DOUBLE_RING/SPIRAL), mở khóa ở cấp 5, bật/tắt bằng `/elementer aura` hoặc nút trong GUI; ẩn khi tàng hình/ngồi xổm/đang lướt/bơi. Tinh chỉnh trong `elements.yml` (mục `aura`) và `config.yml` (mục `aura`).
- Thức tỉnh, EXP, cấp 1–50, điểm kỹ năng, mở khóa kỹ năng theo cấp, nâng cấp từng kỹ năng bằng điểm.
- 26 kỹ năng (18 của 6 nguyên tố thường + 4 Bóng Tối + 4 Rồng) có logic thật (sát thương/khống chế/hồi máu/khiên/lướt) + hiệu ứng hạt và âm thanh:
  Hỏa Cầu, Bùng Cháy, Thiên Hỏa · Băng Tiễn, Sương Giá, Băng Ngục · Lôi Bộ, Sét Đánh, Thiên Lôi · Thạch Giáp, Địa Chấn, Pháo Đài · Phong Bộ, Lưỡi Gió, Lốc Xoáy · Quang Thuẫn, Ánh Sáng Phục Hồi, Thánh Quang.
- Tương khắc vòng tròn Hỏa→Băng→Phong→Thổ→Lôi→Hỏa (Quang trung lập, mạnh hơn trước xác sống); áp dụng cả với quái (`mob-elements`).
- Giới hạn chống lạm dụng: trần sát thương mỗi đòn, hệ số PvP, hồi chiêu từng kỹ năng + hồi chiêu chung, trần thời gian khống chế + miễn nhiễm trói, trần đẩy lùi/khiên/hồi máu/lướt, tối đa số mục tiêu mỗi lần.
- EXP từ khai khoáng (bỏ qua khối do người chơi đặt), giết quái (bỏ qua quái từ spawner/trứng/golem tự dựng), PvP (cooldown cùng nạn nhân, bỏ qua cùng IP, giới hạn chênh cấp), có giới hạn EXP mỗi ngày.
- Phần thưởng mốc cấp chỉ nhận **một lần** cho mỗi người chơi (ghi nhận trong DB trước khi trao).
- SQLite (mọi thao tác DB chạy ở thread riêng, không chặn main thread), tải lỗi thì **không** ghi đè dữ liệu cũ; dữ liệu sai được sửa và ghi log.
- GUI: menu chính, thức tỉnh (bấm 2 lần xác nhận), kỹ năng (nâng cấp/chọn), chỉ số, tương khắc, bảng xếp hạng.

## Chưa có trong bản này (Giai đoạn 2–3)

MySQL, Element Mastery, tích hợp trực tiếp cổng nạp (hiện dùng lệnh console), tinh thể nguyên tố, thử thách, Boss, thành tựu, danh hiệu, ngoại hình/hào quang, nhánh nâng cấp theo nhánh, tổ hợp hai nguyên tố, tích hợp AstraxQuest / AstraxPetRPG / Vault / PlaceholderAPI, hỗ trợ BoxMine (đào nhanh, độ bền cuốc…), `/elementeradmin give crystal`. Cấu hình `database.type: mysql` hiện sẽ cảnh báo và dùng SQLite.

## Bố cục phẳng

Mọi file `.java`, `.yml`, `pom.xml`, `README.md` nằm ở thư mục gốc (một package `com.astrax.elementer`), `pom.xml` đặt `sourceDirectory=${project.basedir}`. File `*Test.java` chỉ biên dịch ở giai đoạn test và không vào JAR. Upload tất cả lên thư mục gốc repo GitHub.

## Build

Yêu cầu: JDK 21, Maven 3.9+, truy cập mạng để tải `paper-api`.

```bash
mvn clean package
```

JAR: `target/AstraxElementer-1.0.0.jar`. Test chạy trong giai đoạn `package`; nếu cần bỏ qua: `-DskipTests`.

## Cài đặt

1. Dùng server Paper 1.21.4, Java 21.
2. Chép JAR vào `plugins/`, khởi động server.
3. Chỉnh `plugins/AstraxElementer/*.yml`, rồi `/elementeradmin reload`.

Driver SQLite được đóng gói sẵn trong JAR (maven-shade), không cần thêm thư viện.

## Lệnh và quyền

| Lệnh | Quyền | Mặc định |
|---|---|---|
| `/elementer` | `elementer.command.main` | mọi người |
| `/elementer info` | `elementer.command.info` | mọi người |
| `/elementer skills` | `elementer.command.skills` | mọi người |
| `/elementer stats` | `elementer.command.stats` | mọi người |
| `/elementer top` | `elementer.command.top` | mọi người |
| `/elementer cast [1-3]` và phím F | `elementer.command.cast` | mọi người |
| `/elementer select <1-3>` | `elementer.command.select` | mọi người |
| `/elementer awaken <nguyên tố>` | `elementer.command.awaken` | mọi người |
| `/elementer unlock <nguyên tố>` | `elementer.command.unlock` | mọi người |
| `/elementer switch <nguyên tố>` | `elementer.command.switch` | mọi người |
| `/elementer aura [on\|off]` | `elementer.command.aura` | mọi người |
| `/elementer wings [on\|off]` | `elementer.command.wings` | mọi người |
| `/elementer exchange [list\|nguyên tố]` | `elementer.command.exchange` | mọi người |
| Chuột phải vé nguyên tố | `elementer.voucher.use` | mọi người |
| `/elementeradmin reload` | `elementer.admin.reload` | op |
| `/elementeradmin give <người chơi> <exp\|points> <số>` | `elementer.admin.give` | op |
| `/elementeradmin give <người chơi> voucher <nguyên tố> [1-10]` | `elementer.admin.give` | op |
| `/elementeradmin give <người chơi> crystal <nguyên tố> [1-64]` | `elementer.admin.give` | op |
| `/elementeradmin boss <list\|spawn <id>\|kill <id\|all>>` | `elementer.admin.boss` | op |
| `/elementeradmin boss setspawn` / `clearspawn` / `spawnnow [id]` / `next` | `elementer.admin.boss` | op |
| `/elementeradmin set <người chơi> <element\|level\|exp\|points> <giá trị>` | `elementer.admin.set` | op |
| `/elementeradmin reset <người chơi>` | `elementer.admin.reset` | op |
| `/elementeradmin updateconfig` | `elementer.admin.updateconfig` | op |

`elementer.admin.*` gồm toàn bộ quyền quản trị. Lệnh admin hiện chỉ áp dụng cho người chơi **đang online**.

**Cách dùng kỹ năng:** phím **F** (đổi tay) = tung kỹ năng đang chọn; **Shift+F** = đổi kỹ năng; hoặc `/elementer cast <1-3>`. Có thể tắt phím F trong `config.yml` (`input.swap-key-cast`).

## Cấu hình

| File | Nội dung |
|---|---|
| `config.yml` | DB, EXP/cấp, nguồn EXP, phần thưởng mốc cấp, giới hạn chiến đấu |
| `elements.yml` | Thông tin/nội tại 8 nguyên tố, tương khắc, nguyên tố của quái, danh sách xác sống |
| `skills.yml` | 26 kỹ năng: sát thương, tầm, bán kính, hồi chiêu, hiệu ứng, hạt, âm thanh |
| `bosses.yml` | Boss thường + huyền thoại, kỹ năng boss, bộ spawn định kỳ (`spawner`), tỉ lệ, Tinh Thể, giá đổi vé, điểm hồi sinh cố định |
| `spawner-state.yml` | (tự tạo) điểm spawn boss do `boss setspawn` đặt và mốc spawn gần nhất |
| `messages.yml` | Toàn bộ thông báo và chữ trong GUI (hỗ trợ `&` và `&#rrggbb`) |

Mẹo cân bằng: sát thương thực = `damage × (1 + per-level-bonus×(cấp kỹ năng−1)) × (1 + damage-per-level×(cấp−1)) × tương khắc × (PvP 0.6)`, và luôn ≤ `combat.max-skill-damage`.

## Cập nhật

1. Sao lưu `plugins/AstraxElementer/` (đặc biệt `data.db`).
2. Thay JAR mới, khởi động lại.
3. Plugin **không tự ghi đè** file YAML đã có. Sau khi cập nhật:
   - `messages.yml` thiếu tin nhắn mới: plugin tự dùng giá trị mặc định trong JAR (có cảnh báo trong console) nên không còn hiện `[thiếu tin nhắn: ...]`.
   - `elements.yml`, `skills.yml`, `bosses.yml` thiếu nguyên tố/kỹ năng/boss mới: console cảnh báo danh sách mục thiếu. Chạy **`/elementeradmin updateconfig`** để sao lưu 4 file (`elements`, `skills`, `bosses`, `messages`) vào thư mục `backup-<thời gian>` rồi ghi đè bằng bản mới và tải lại (mất chỉnh sửa của bạn trong 4 file đó, có thể chép lại từ bản sao lưu). Hoặc xóa file cần cập nhật rồi khởi động lại để plugin tạo lại.
   - `config.yml`: khóa thiếu tự dùng giá trị mặc định; file được giữ nguyên.

## Kiểm thử

Bộ test JUnit 5 (`src/test`): EXP/lên cấp, hồi chiêu, công thức sát thương + tương khắc, kiểm tra/sửa dữ liệu hỏng, lưu/tải SQLite, **chống nhận thưởng trùng** (kể cả 50 yêu cầu đồng thời), xếp hạng, khai báo quyền trong `plugin.yml`, và kiểm tra YAML mẫu (đủ 8 nguyên tố, 26 kỹ năng, trong trần).

Đã thực sự chạy ở môi trường soạn thảo: các test thuần logic (`ExpCurveTest`, `CooldownTrackerTest`, `DamageCalculatorTest`, `DataSanitizerTest`, `WingsGeometryTest` – 26 test đạt) và kiểm tra cú pháp toàn bộ mã nguồn. **Chưa chạy:** test SQLite, test YAML/quyền (cần Maven + thư viện) và mọi thứ cần server thật.

**Chưa có test tự động** cho các tình huống cần server (chết, thoát game, đổi thế giới, đổi mục tiêu, spam kỹ năng). Danh sách kiểm tra thủ công trên server test:

- [ ] Thức tỉnh → thoát → vào lại: nguyên tố/cấp/EXP còn nguyên.
- [ ] Nhận vé thứ hai (`/elementeradmin give <tên> voucher <nguyên tố>`), chuột phải: có nguyên tố mới ở cấp 1; chuyển đi/quay lại: cấp cũ còn nguyên; vé thứ tư bị từ chối mà không mất vé.
- [ ] Triệu hồi boss (`/elementeradmin boss spawn fire_lord`): thanh máu, lời thoại, đòn vùng có vòng báo hiệu; hạ boss: nhận Tinh Thể/EXP/vật phẩm; đổi 48 Tinh Thể ở GUI ra vé.
- [ ] `/elementeradmin boss setspawn` rồi `/elementeradmin boss spawnnow`: boss xuất hiện tại điểm đặt, thông báo toàn server; `boss next` hiện thời gian còn lại; `spawnnow dark_lord` hiện tiêu đề huyền thoại, boss kéo người chơi về phía mình.
- [ ] Để boss không bị hạ quá `spawner.despawn-minutes`: boss biến mất; restart server: giờ đếm tiếp tục từ `spawner-state.yml`.
- [ ] Kéo Hồn (Bóng Tối) kéo mục tiêu về trước mặt và không hất tung/bay.
- [ ] Cánh rồng và hào quang hiển thị đúng sau lưng/dưới chân, tắt được bằng `/elementer wings off` và `/elementer aura off`.
- [ ] Spam phím F: chỉ tung theo hồi chiêu chung + hồi chiêu kỹ năng.
- [ ] Chết khi đang có khiên: khiên mất; hồi sinh: nội tại (máu/tốc độ…) áp dụng lại.
- [ ] Thoát giữa Thiên Lôi/Thiên Hỏa: không lỗi console, đòn tiếp theo không gây sát thương.
- [ ] Đổi thế giới giữa lúc đang cast: kỹ năng trễ bị hủy.
- [ ] Mục tiêu chết/đổi giữa chừng, người chơi creative/spectator, thế giới tắt PvP: không bị tác động.
- [ ] Đặt rồi đào lại quặng: không có EXP; đào quặng tự nhiên: có EXP; quái spawner: không có EXP.
- [ ] Đạt cấp 10/25/50 hai lần (reset rồi lên lại): phần thưởng chỉ trao một lần.
- [ ] Vùng WorldGuard cấm PvP: kỹ năng không gây sát thương/khống chế người chơi.

## Giới hạn đã biết

- **Boss huyền thoại:** kỹ năng/độ khó chưa thử trên server thật (3000 máu, ST tối đa 30/đòn); Kéo Hồn của boss cũng dịch chuyển người chơi bằng sự kiện teleport chuẩn. Boss spawn định kỳ giữ chunk bằng chunk ticket cho tới khi chết/biến mất. Nếu server tắt đúng lúc đến giờ, boss sẽ spawn ngay lần kiểm tra đầu tiên sau khi bật lại.
- **Boss:** không lưu xuống đĩa (restart là biến mất; điểm hồi sinh trong `bosses.yml` sẽ tạo lại khi có người chơi lại gần). Chỉ người **đang online** và cùng thế giới lúc boss chết mới nhận thưởng. Boss dùng quái vanilla nên AI di chuyển/đánh thường là của Minecraft; kỹ năng nguyên tố là phần bổ sung. Chưa kiểm chứng độ khó/cân bằng trên server thật: chỉnh `health`, `damage-multiplier` trong `bosses.yml`. Các kiểu quái `PILLAGER`, `STRAY`, `SKELETON` còn bắn tên vanilla song song với kỹ năng.
- Boss còn có xác suất rớt thẳng **Vé Nguyên Tố** (`rewards.voucher-chance`, mặc định 5%/người nhận thưởng; không rớt nếu đã sở hữu nguyên tố đó hoặc đã đủ 3).
- **Kéo Hồn** (Bóng Tối) kéo người về trước mặt và **không hất tung** (xóa đẩy lùi/rơi tự do sau khi kéo).
- **Đổi vé** bị từ chối khi bạn đã sở hữu nguyên tố đó hoặc đã đủ 3 nguyên tố (vé sẽ không dùng được).

- Cánh rồng và hào quang là hiệu ứng hạt (chỉ trang trí, **không cho bay**). Cánh dùng khoảng 55 hạt mỗi nhịp, hào quang Rồng khoảng 40: nếu server đông người dùng Rồng hãy tăng `aura.interval-ticks`.
- Chưa có lệnh bỏ/đổi một nguyên tố đã sở hữu (giới hạn 3 là vĩnh viễn trừ khi quản trị `reset`). Vé giới hạn có thể giao dịch/bán lại giữa người chơi.
- **Kéo Hồn** dịch chuyển cả người chơi khác bằng sự kiện teleport chuẩn (plugin vùng bảo vệ có thể hủy) và chỉ kéo khi đòn sát thương không bị hủy; tắt hẳn việc kéo người chơi bằng `combat.teleport-pull-players: false`.
- Biểu tượng nguyên tố (emoji/ký tự đặc biệt) có thể hiển thị ô vuông tùy font client; đổi `symbol` trong `elements.yml`.
- Hào quang gửi hạt cho mọi người chơi ở gần: server đông có thể tăng `aura.interval-ticks` hoặc tắt `aura.enabled`. Mỗi người chơi vẽ tối đa ~12 hạt mỗi nhịp.
- Nâng cấp từ bản cũ: các cột/bảng mới (`unlocked`, `aura_enabled`, `wings_enabled`, `element_progress`) được thêm tự động vào DB. Người chơi cũ mặc định sở hữu nguyên tố đang dùng; **cấp/EXP/điểm kỹ năng cũ (dùng chung) được gán cho nguyên tố đang dùng, các nguyên tố khác đã có sẽ bắt đầu lại từ cấp 1**. Cơ chế này mới có test viết sẵn (chưa chạy được ngoài Maven).
- Bảng xếp hạng xếp theo cấp của nguyên tố đang dùng. Điểm giới hạn EXP mỗi ngày (`exp.daily-cap`) tính chung cho cả người chơi. Boss/EXP luôn cộng vào nguyên tố **đang dùng**.
- Bảng xếp hạng hiển thị nguyên tố đang dùng.

- Chưa biên dịch với Paper API thật: có thể còn lỗi biên dịch nhỏ ở các lớp dùng Bukkit (tên API đã viết theo Paper 1.21.4 nhưng chưa được compiler xác nhận). Gửi log `mvn` để sửa.
- Không có hệ thống party: **Ánh Sáng Phục Hồi** hồi cho mọi người chơi trong bán kính (có thể tắt bằng `effects.heal-others` trong `skills.yml`).
- Lá chắn trừ vào sát thương gốc trước giảm giáp, và hủy luôn đẩy lùi khi hấp thụ hết.
- Sát thương kỹ năng đi qua sự kiện sát thương chuẩn nên plugin bảo vệ vùng có thể hủy; khi đó khống chế/đẩy lùi cũng không áp dụng. Plugin chống gian lận có thể cảnh báo cú lướt (Lôi Bộ/Phong Bộ).
- Theo dõi khối do người chơi đặt nằm trong bộ nhớ (tối đa 50.000 khối gần nhất, mất khi restart); khối bị piston đẩy không được theo dõi.
- Phần thưởng mốc cấp chạy lệnh console từ `config.yml` – chỉ cấu hình lệnh bạn tin tưởng. Cấp được đặt bằng `/elementeradmin set level` không tự trao thưởng mốc.
- `/elementeradmin reset` xóa tiến trình nhưng **giữ** lịch sử nhận thưởng (để không nhận lại).
