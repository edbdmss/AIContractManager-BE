import fitz
from PIL import Image
import io


def pdf_to_images(pdf_bytes: bytes) -> list[Image.Image]:
    images = []

    with fitz.open(stream=pdf_bytes, filetype="pdf") as doc:
        for page in doc:
            pix = page.get_pixmap(
                matrix=fitz.Matrix(2, 2),
                alpha=False
            )

            image = Image.open(
                io.BytesIO(pix.tobytes("png"))
            ).convert("RGB")

            images.append(image)

    return images