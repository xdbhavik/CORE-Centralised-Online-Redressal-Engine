import os
import glob
import re

def fix_shadows():
    files = glob.glob('lib/features/**/*.dart', recursive=True) + glob.glob('lib/core/**/*.dart', recursive=True)
    for f in files:
        if not os.path.isfile(f): continue
        with open(f, 'r') as file:
            content = file.read()
        
        # Fix BoxShadow list issue
        content = re.sub(r'const\s+\[(AppShadows\.level\d+)\]', r'\1', content)
        
        # Fix ElevatedButton issues
        # text_complaint_screen.dart and image_complaint_screen.dart have:
        # minimumSize: const Size.doubleInfinity, 56),
        # shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(28)),
        # elevation: canSubmit ? 2 : 0,
        # ),
        # child: Row(
        # They should be minimumSize: const Size(double.infinity, 56),
        content = content.replace('const Size.doubleInfinity,', 'const Size(double.infinity,')
        
        # fix child missing issue in ElevatedButton
        # actually, the closing bracket for styleFrom was placed wrong:
        # ElevatedButton.styleFrom(..., minimumSize: const Size(double.infinity, 56)),
        content = content.replace('minimumSize: const Size(double.infinity, 56),', 'minimumSize: const Size(double.infinity, 56),')
        
        with open(f, 'w') as file:
            file.write(content)

fix_shadows()
