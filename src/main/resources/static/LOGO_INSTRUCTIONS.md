## Logo Placeholder Instructions

Since we can't create binary PNG files directly, you need to add a logo manually:

### Option 1: Use Your Existing Logo
If you have a logo/favicon for CheckItOut, copy it to:
```
checkItOut-be/src/main/resources/static/favicon.png
```

### Option 2: Create a Simple Logo
1. Use any image editor (Paint, GIMP, Photoshop)
2. Create a 256x256px image
3. Add your company initial "C" or "CheckItOut" text
4. Save as PNG with transparent background
5. Place at the path above

### Option 3: Use Online Logo Generator
1. Go to https://favicon.io/favicon-generator/
2. Enter "C" or "CO" for CheckItOut
3. Download the favicon package
4. Use the `android-chrome-256x256.png` file
5. Rename it to `favicon.png` and place in the resources folder

### Option 4: Use a Placeholder (Temporary)
Download a generic QR logo placeholder from:
- https://via.placeholder.com/256/4CAF50/FFFFFF?text=C
- Save as `favicon.png` in the resources folder

## Testing Without Logo

The QR code will work fine without a logo. The backend will:
1. Try to find the logo file
2. If not found, generate a QR code without logo
3. QR code will still be scannable

## Verification

After adding the logo and restarting the backend, you should see:
- Log message: "Logo found at: static/favicon.png"
- QR code with your logo embedded in the center
- High error correction ensures the QR remains scannable
