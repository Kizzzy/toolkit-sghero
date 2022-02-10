package cn.kizzzy.sghero.display;

import cn.kizzzy.helper.LogHelper;
import cn.kizzzy.javafx.display.Display;
import cn.kizzzy.javafx.display.DisplayAAA;
import cn.kizzzy.javafx.display.DisplayAttribute;
import cn.kizzzy.javafx.display.DisplayFrame;
import cn.kizzzy.javafx.display.DisplayTrack;
import cn.kizzzy.javafx.display.DisplayTracks;
import cn.kizzzy.javafx.display.DisplayType;
import cn.kizzzy.vfs.IPackage;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;

@DisplayAttribute(suffix = {
    "png",
})
public class PngDisplay extends Display<IPackage> {
    
    public PngDisplay(IPackage context, String path) {
        super(context, path);
    }
    
    @Override
    public DisplayAAA load() {
        try {
            byte[] data = context.load(path, byte[].class);
            if (data != null) {
                BufferedImage image = ImageIO.read(new ByteArrayInputStream(data));
                if (image != null) {
                    DisplayFrame frame = new DisplayFrame();
                    frame.x = 200;
                    frame.y = 200;
                    frame.width = image.getWidth();
                    frame.height = image.getHeight();
                    frame.image = image;
                    
                    DisplayTrack track = new DisplayTrack();
                    track.frames.add(frame);
                    
                    DisplayTracks tracks = new DisplayTracks();
                    tracks.tracks.add(track);
                    
                    return new DisplayAAA(DisplayType.SHOW_IMAGE, tracks);
                }
            }
        } catch (Exception e) {
            LogHelper.error("init failed: ", e);
        }
        return null;
    }
}
