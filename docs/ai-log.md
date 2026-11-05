# NHẬT KÝ SỬ DỤNG AI (AI LOG)

## Quy định
File này ghi lại cách nhóm sử dụng các công cụ AI (như Claude, Gemini, Qwen, Copilot...) trong suốt quá trình làm dự án.

## Mục 1: Tóm tắt sử dụng AI
| Giai đoạn | Công cụ AI | Mục đích sử dụng | Mức độ |
| :--- | :--- | :--- | :--- |
| M1 - Lên ý tưởng & Thiết kế | Qwen / Claude | Thảo luận kiến trúc, so sánh TCP thuần vs WebSocket, thiết kế 4-byte framing. | Co-write |
| M1 - Viết tài liệu | Qwen | Soạn thảo nội dung proposal.md, protocol-design.md, architecture.md. | Co-write |
| M2 - Code Skeleton | Qwen | Viết code mẫu ChatServer, ClientHandler, xử lý TCP stream. | Co-write |

## Mục 2: Những quyết định do nhóm tự đưa ra
- Nhóm quyết định chuyển từ WebSocket sang **TCP Socket thuần** để đáp ứng tốt nhất yêu cầu về "Message Framing" của giảng viên.
- Nhóm tự thiết kế cấu trúc JSON và bảng Command/Event dựa trên tính năng Chat nhiều phòng.
- Nhóm quyết định dùng mô hình Thread-per-client và cơ chế `synchronized` block cho việc ghi socket.

## Mục 3: Những trường hợp AI đưa ra kết quả sai và cách nhóm phát hiện
- **Lỗi của AI:** Ban đầu, AI đề xuất sử dụng thư viện Java-WebSocket để nhanh chóng có kết quả. 
- **Cách nhóm phát hiện:** Khi đối chiếu với rubric chấm điểm (tiêu chí A2 về Framing), nhóm nhận thấy WebSocket tự động xử lý framing, làm giảm giá trị thể hiện kỹ thuật lập trình mạng.
- **Cách khắc phục:** Nhóm đã yêu cầu AI viết lại toàn bộ logic đọc/ghi stream bằng `DataInputStream` và `DataOutputStream` với cơ chế 4-byte length-prefix thủ công.