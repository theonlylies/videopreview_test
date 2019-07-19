package ru.anim;

import org.bytedeco.ffmpeg.avutil.AVRational;
import org.bytedeco.javacv.*;

import javax.imageio.ImageIO;
import java.io.*;

import static org.bytedeco.ffmpeg.global.avcodec.*;
import static org.bytedeco.ffmpeg.global.avutil.*;

public class Test {

    public Test() throws IOException {
        // debug stuff
//        FFmpegLogCallback.set();
//        av_log_set_level(AV_LOG_DEBUG);
        FFmpegFrameGrabber frameGrabber = new FFmpegFrameGrabber("D:\\480.mp4");
        frameGrabber.start();
        Frame frame = frameGrabber.grabImage();
//        CanvasFrame canvasFrame = new CanvasFrame("LOL");
//        canvasFrame.setCanvasSize(frame.imageWidth, frame.imageHeight);
        final int FRAME_COUNT = (int) frameGrabber.getFrameRate() / 2;
        final int FRAME_COUNT_ORIG = (int) frameGrabber.getFrameRate();
        double aspectRatio = (double) frame.imageWidth / frame.imageHeight;
        int width = frame.imageWidth > 480 ? 480 : frame.imageWidth;
        int height = (int) (width * aspectRatio);
        FFmpegFrameRecorder frameRecorder = new FFmpegFrameRecorder("test.webm", width, height, 0);
        frameRecorder.setFrameRate(FRAME_COUNT);
        frameRecorder.setVideoCodec(AV_CODEC_ID_VP8);
        frameRecorder.setFormat("webm");
        frameRecorder.setAudioChannels(0);
        frameRecorder.start();


        for (int stage = 1, i = 0; stage < 4; stage++) {
            i = 0;
            double part = frameGrabber.getLengthInVideoFrames() / 100;
            long startT = (long) (part * 15), midT = (long) (part * 50), lastT = (long) (part * 85);
            switch (stage) {
                case 1:
                    frameGrabber.setFrameNumber((int) startT);
                    frame = frameGrabber.grabImage();
                    File outputfile = new File("image.jpg");
                    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                    ImageIO.write(Java2DFrameUtils.toBufferedImage(frame), "jpg", outputfile);// output file maybe a stream
                    break;
                case 2:
                    frameGrabber.setFrameNumber((int) midT);
                    break;
                case 3:
                    frameGrabber.setFrameNumber((int) lastT);
                    break;

            }

            while ( (frame = frameGrabber.grabImage()) != null && i < FRAME_COUNT_ORIG) {
                //canvasFrame.showImage(frame);
                frameRecorder.record(frame);
                i++;
            }
        }
        frameRecorder.stop();
        frameRecorder.close();
//        canvasFrame.dispose();
    }


    static public void main(String[] args) throws IOException {
        System.out.println("LOL");
        Test test = new Test();
    }
}
