import sys
import os
import cv2
import numpy as np
from paddleocr import PaddleOCR
from pdf2image import convert_from_path
import fitz
import gc

def optimize_ocr_processing(img):
    # Simplified preprocessing pipeline
    img_np = np.array(img)
    if len(img_np.shape) == 3:
        gray = cv2.cvtColor(img_np, cv2.COLOR_RGB2GRAY)
    else:
        gray = img_np.copy()
    
    # Adaptive thresholding with size reduction
    gray = cv2.resize(gray, None, fx=0.5, fy=0.5)
    return cv2.adaptiveThreshold(gray, 255, 
        cv2.ADAPTIVE_THRESH_GAUSSIAN_C, cv2.THRESH_BINARY, 11, 2)

def process_pdf(input_pdf_path, output_pdf_path):
    try:
        # Set environment variables for memory optimization
        os.environ["FLAGS_allocator_strategy"] = "naive_best_fit"
        os.environ["OMP_NUM_THREADS"] = "1"

        # Reduced DPI for smaller images
        images = convert_from_path(input_pdf_path, 
            dpi=300,
            grayscale=True,
            thread_count=2)

        # Optimized OCR configuration
        ocr = PaddleOCR(
            use_angle_cls=False,
            lang='en',
            use_gpu=False,
            enable_mkldnn=True,        # Enable MKL-DNN acceleration
            cpu_threads=2,             # Limit CPU threads
            det_limit_side_len=1024,   # Reduce max detection size
            det_db_thresh=0.3,         # Higher threshold for detection
            det_db_box_thresh=0.5,
            det_db_unclip_ratio=2.0,
            rec_algorithm='SVTR_LCNet',# Lightweight recognition
            det_algorithm='DB',        # Efficient detection algorithm
            use_pdserving=False,
            use_tensorrt=False,
            drop_score=0.5,            # Higher confidence threshold
            rec_image_shape='3,48,320' # Smaller input size
        )

        doc = fitz.open()

        for img in images:
            processed = optimize_ocr_processing(img)
            result = ocr.ocr(processed, cls=False)
            
            page = doc.new_page(width=img.width, height=img.height)
            
            for line in (result[0] or []):
                points = line[0]
                text_info = line[1]
                
                if text_info[1] < 0.6:  # Confidence threshold
                    continue
                    
                x_coords = [p[0]*2 for p in points]  # Scale back coordinates
                y_coords = [p[1]*2 for p in points]
                rect = fitz.Rect(min(x_coords), min(y_coords), 
                               max(x_coords), max(y_coords))
                
                page.insert_textbox(
                    rect * 2,  # Account for 0.5x scaling
                    text_info[0],
                    fontsize=max(6, int(rect.height)),
                    color=(0,0,0),
                    overlay=True
                )
            
            # Manual garbage collection
            del processed
            gc.collect()

        if doc.page_count == 0:
            return False
        
        doc.save(output_pdf_path)
        return True

    except Exception as e:
        print(f"[ERROR] {e}", file=sys.stderr)
        return False
