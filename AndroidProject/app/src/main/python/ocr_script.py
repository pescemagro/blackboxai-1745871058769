import sys
import fitz  # PyMuPDF
from paddleocr import PaddleOCR
from pdf2image import convert_from_path
import cv2
import numpy as np

def main(pdf_path):
    # Initialize OCR with English language and CPU mode
    ocr = PaddleOCR(use_angle_cls=True, lang='en', use_gpu=False)

    # Convert PDF pages to images
    images = convert_from_path(pdf_path, dpi=300)

    results_text = []

    for img in images:
        # Convert PIL image to OpenCV format
        img_cv = cv2.cvtColor(np.array(img), cv2.COLOR_RGB2BGR)

        # Run OCR on the image
        result = ocr.ocr(img_cv, cls=True)

        # Extract text from OCR result
        page_text = []
        for line in result:
            for res in line:
                page_text.append(res[1][0])
        results_text.append('\n'.join(page_text))

    # Combine all page texts
    full_text = '\n\n'.join(results_text)
    return full_text

if __name__ == "__main__":
    pdf_path = sys.argv[1]
    text = main(pdf_path)
    print(text)
