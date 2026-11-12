# Documents API

All endpoints require `Authorization: Bearer <access-token>`. The contract must belong to the authenticated user and must not be deleted.

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/api/aicontract/contracts/{contractId}/documents` | List document metadata |
| GET | `/api/aicontract/contracts/{contractId}/documents/{id}` | Read document metadata |
| POST | `/api/aicontract/contracts/{contractId}/documents` | Upload a multipart `file` (201) |
| GET | `/api/aicontract/contracts/{contractId}/documents/{id}/download` | Download the original file as an attachment |
| DELETE | `/api/aicontract/contracts/{contractId}/documents/{id}` | Soft-delete metadata (204) |

Supported files: PDF, HWP, HWPX, DOCX, JPG/JPEG, PNG. Google Docs should be exported to PDF or DOCX before upload. Maximum size is 20 MB. The server checks the filename extension and initial file signature, stores the bytes under a random key, and returns metadata without exposing the storage path.

Local files are stored in `DOCUMENT_STORAGE_DIR` (default `./var/documents`). This directory is not committed to Git. Document DELETE hides the record but **does not physically remove the file**; permanent deletion and retention policy must be implemented before handling production data. Local storage also needs a persistent volume when deployed in a container.
