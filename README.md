# OnePlus Data Protector
This app can be used to maximize the protection of the data on your rooted phone in situations in which someone has physical access to your device. It only works on OnePlus One 64GB phones (and might work on the 16GB edition).

## Features
- Toggling tamper flag
- Unlocking/locking bootloader without wiping your data
- Disabling and enabling the recovery program (such as TWRP) from within Android

## Use case
Suppose you have rooted your phone and in the process you have also installed a custom recovery program. Anyone with physical access to your phone can now easily extract all files that are on your internal storage, by booting the phone into recovery mode and connecting it to a computer. Imagine that, to counter this problem, you install a recovery program that does not expose any files and only accepts signed flashable packages. Now, an intruder cannot access files through recovery mode, but can still flash a new recovery program that does expose files. To prevent this from happening, one must also lock the bootloader, to prevent malicious images from being flashed. This app makes taking these measures easy. It is able to completely block access to the recovery program by backing up and erasing the recovery partition, and toggle the bootloader lock. This will make it (nearly) impossible for anyone without special hardware, tools and soldering equipment and experience to gain access to your files.
 
## Tested on
- OnePlus One 64GB, Resurrection Remix

## Important
If you lock your bootloader and disable your recovery, the only way to get to your data is through Android. If you corrupt Android in one way or another, i.e. it does not boot anymore, you have practically lost your data. I advise you to only disable access to the recovery program in situations in which there is an increased likelihood your phone will be stolen.
