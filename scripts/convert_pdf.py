#!/usr/bin/env python3
"""
PDF to Word Converter using pdf2docx
Giữ format tốt, tối ưu cho mục lục và tab leaders
"""

import sys
from pdf2docx import Converter

def convert_pdf_to_docx(pdf_path, docx_path):
    """Convert PDF to DOCX with formatting preserved"""
    try:
        cv = Converter(pdf_path)
        
        # Tham số tối ưu để giữ layout tốt hơn
        cv.convert(
            docx_path,
            # Giữ nguyên layout gốc
            multi_processing=False,
            # Tăng độ chính xác khi parse
            connected_border_tolerance=0.5,
            # Giảm threshold để không gộp các dòng
            line_separate_threshold=0,
            # Giữ khoảng cách giữa các ký tự
            min_section_height=15,
            # Tối ưu cho text có tab/spaces
            delete_end_line_hyphen=False
        )
        cv.close()
        print(f"SUCCESS: {docx_path}")
        return True
    except TypeError:
        # Nếu version cũ không hỗ trợ params, dùng mặc định
        try:
            cv = Converter(pdf_path)
            cv.convert(docx_path)
            cv.close()
            print(f"SUCCESS: {docx_path}")
            return True
        except Exception as e:
            print(f"ERROR: {str(e)}", file=sys.stderr)
            return False
    except Exception as e:
        print(f"ERROR: {str(e)}", file=sys.stderr)
        return False

if __name__ == "__main__":
    if len(sys.argv) != 3:
        print("Usage: python convert_pdf.py <input.pdf> <output.docx>", file=sys.stderr)
        sys.exit(1)
    
    pdf_path = sys.argv[1]
    docx_path = sys.argv[2]
    
    success = convert_pdf_to_docx(pdf_path, docx_path)
    sys.exit(0 if success else 1)
