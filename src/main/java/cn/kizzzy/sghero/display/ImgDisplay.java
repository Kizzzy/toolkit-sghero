package cn.kizzzy.sghero.display;

import cn.kizzzy.helper.LogHelper;
import cn.kizzzy.javafx.display.DisplayParam;
import cn.kizzzy.javafx.display.DisplayType;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Collections;

@DisplayFlag(suffix = {
    "png",
})
public class ImgDisplay extends Display {
    
    
    public ImgDisplay(DisplayContext context, String path) {
        super(context, path);
    }
    
    @Override
    public void init() {
        try {
            byte[] data = context.load(path, byte[].class);
            if (data != null) {
                BufferedImage image = ImageIO.read(new ByteArrayInputStream(data));
                DisplayParam param = new DisplayParam.Builder()
                    .setX(200)
                    .setY(200)
                    .setWidth(image.getWidth())
                    .setHeight(image.getHeight())
                    .setImage(image)
                    .build();
                context.notifyListener(DisplayType.SHOW_IMAGE, Collections.singletonList(param));
            }
        } catch (Exception e) {
            LogHelper.error("init failed: ", e);
        }
    }
}
