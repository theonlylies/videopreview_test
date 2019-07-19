package ru.anim;

import java.io.*;
import java.nio.IntBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.util.Map;

import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacpp.*;
import org.bytedeco.ffmpeg.avcodec.*;
import org.bytedeco.ffmpeg.avformat.*;
import org.bytedeco.ffmpeg.avutil.*;
import org.bytedeco.ffmpeg.swscale.*;
import org.bytedeco.javacv.FFmpegFrameGrabber;

import static org.bytedeco.ffmpeg.global.avcodec.*;
import static org.bytedeco.ffmpeg.global.avformat.*;
import static org.bytedeco.ffmpeg.global.avutil.*;
import static org.bytedeco.ffmpeg.global.swscale.*;

public class App {
    /**
     * Write image data using simple image format ppm
     *
     * @see https://en.wikipedia.org/wiki/Netpbm_format
     */
    static void save_frame(AVFrame pFrame, int width, int height, int f_idx, String prefix) throws IOException {
        // Open file
        String szFilename = String.format("frame_%s_%d_.png", prefix, f_idx);
        OutputStream pFile = new FileOutputStream(szFilename);

        // Write header
        pFile.write(String.format("P6\n%d %d\n255\n", width, height).getBytes());

        // Write pixel data
        BytePointer data = pFrame.data(0);
        byte[] bytes = new byte[width * 3];
        int l = pFrame.linesize(0);
        for (int y = 0; y < height; y++) {
            data.position(y * l).get(bytes);
            pFile.write(bytes);
        }

        // Close file
        pFile.close();
    }

    static int seek_time(AVFormatContext fmt_ctx, int stream_index, double time) throws IllegalArgumentException {
//        if (avformat_find_stream_info(fmt_ctx, (PointerPointer) null) < 0) {
//            throw new IllegalArgumentException("Can't find stream in AV context, maybe you forget open context");
//        }
        long dur = fmt_ctx.duration();
        if (time < 0 || time >= dur * AV_TIME_BASE) {
            throw new IllegalArgumentException("Can't find stream in AV context, maybe you forget open context");
        }
        long timeAV = (long) (time * AV_TIME_BASE);
        long min_time = timeAV, max_time = timeAV;
        if (!(timeAV - AV_TIME_BASE <= 0 || timeAV + AV_TIME_BASE >= dur)) {
            min_time -= AV_TIME_BASE;
            max_time += AV_TIME_BASE;
        }
        int sa = 0;
        if ((sa = fmt_ctx.streams(stream_index).discard()) >= AVDISCARD_ALL) {
            throw new IllegalArgumentException("Stream discarded!");
        }
        long rescaledTime = av_rescale_q(timeAV, new AVRational().num(1).den(AV_TIME_BASE), fmt_ctx.streams(stream_index).time_base());
        return avformat_seek_file(fmt_ctx, stream_index, rescaledTime, rescaledTime, rescaledTime, AVSEEK_FLAG_BACKWARD);
//        return av_seek_frame(fmt_ctx, stream_index, rescaledTime, AVSEEK_FLAG_BACKWARD);
    }

    public static void main(String[] args) throws Exception {
        avutil.av_log_set_level(AV_LOG_DEBUG);
        System.out.println("Read few frame and write to image");
        avcodec_register_all();
        //Video save init
        av_log_set_level(AV_LOG_DEBUG);
        AVCodec videoCodec = avcodec_find_encoder(AV_CODEC_ID_VP9);
        AVOutputFormat outFmt = av_guess_format("mov", null, null);
        AVFormatContext outFmtCtx = new AVFormatContext();
        avformat_alloc_output_context2(outFmtCtx, outFmt, "", "");
        AVStream outStrm = avformat_new_stream(outFmtCtx, videoCodec);

        avio_open(outFmtCtx.pb(), "test.webm", AVIO_FLAG_WRITE);
        avformat_write_header(outFmtCtx, new AVDictionary());
        AVFormatContext vFormatCtx = new AVFormatContext();
        AVCodecContext vcCtx;

        if (videoCodec == null) {
            System.out.println("Can't find codec");
            throw new IllegalStateException();
        }
        vcCtx = avcodec_alloc_context3(videoCodec);
        if (vcCtx == null) {
            System.out.println("Could not allocate video codec context");
            throw new IllegalStateException();
        }
        vcCtx
                .bit_rate(400000)
                .width(320)
                .height(180)
                .time_base(new AVRational().num(1).den(AV_TIME_BASE))
                .gop_size(10)
                .max_b_frames(1)
                .pix_fmt(AV_PIX_FMT_YUV420P);
        if (avcodec_open2(vcCtx, videoCodec, new AVDictionary()) < 0) {
            System.out.println("Could not open codec");
            throw new IllegalStateException();
        }
        OutputStream pFile = new BufferedOutputStream(new FileOutputStream("test.webp"));
//        WritableByteChannel channel = Channels.newChannel(pFile);
        pFile.write(vcCtx.extradata().asByteBuffer().array());
        pFile.flush();

        //Video save


        int ret = -1, i = 0, v_stream_idx = -1;
        args = new String[1];
        args[0] = "D:\\sample.mp4";
        String vf_path = args[0];
        AVFormatContext fmt_ctx = new AVFormatContext(null);

        AVPacket pkt = new AVPacket();
        AVPacket videoPkt = new AVPacket();

        ret = avformat_open_input(fmt_ctx, vf_path, null, null);
        if (ret < 0) {
            System.out.printf("Open video file %s failed \n", vf_path);
            throw new IllegalStateException();
        }
        System.out.printf("Duration of AVFormatContext:%s,%ss,d/1000*60: %s\n", fmt_ctx.duration(), fmt_ctx.duration() / 1000000, fmt_ctx.duration() / (60 * 60));

        // i dont know but without this function, sws_getContext does not work
        if (avformat_find_stream_info(fmt_ctx, (PointerPointer) null) < 0) {
            System.exit(-1);
        }

//        av_dump_format(fmt_ctx, 0, args[0], 0);

        for (i = 0; i < fmt_ctx.nb_streams(); i++) {
            if (fmt_ctx.streams(i).codecpar().codec_type() == AVMEDIA_TYPE_VIDEO) {
                v_stream_idx = i;
                break;
            }
        }
        if (v_stream_idx == -1) {
            System.out.println("Cannot find video stream");
            throw new IllegalStateException();
        } else {
            System.out.printf("Video stream %d with resolution %dx%d\n", v_stream_idx,
                    fmt_ctx.streams(i).codecpar().width(),
                    fmt_ctx.streams(i).codecpar().height());
        }

        AVCodecContext codec_ctx = avcodec_alloc_context3(null);
        avcodec_parameters_to_context(codec_ctx, fmt_ctx.streams(v_stream_idx).codecpar());

        AVCodec codec = avcodec_find_decoder(codec_ctx.codec_id());
        if (codec == null) {
            System.out.println("Unsupported codec for video file");
            throw new IllegalStateException();
        }
        ret = avcodec_open2(codec_ctx, codec, (PointerPointer) null);
        if (ret < 0) {
            System.out.println("Can not open codec");
            throw new IllegalStateException();
        }

        AVFrame frm = av_frame_alloc();

        // Allocate an AVFrame structure
        AVFrame pFrameRGB = av_frame_alloc();
        if (pFrameRGB == null) {
            System.exit(-1);
        }

        // Determine required buffer size and allocate buffer
        int numBytes = av_image_get_buffer_size(AV_PIX_FMT_YUV420P, codec_ctx.width(),
                codec_ctx.height(), 1);
        BytePointer buffer = new BytePointer(av_malloc(numBytes));

        SwsContext sws_ctx = sws_getContext(
                codec_ctx.width(),
                codec_ctx.height(),
                codec_ctx.pix_fmt(),
                codec_ctx.width(),
                codec_ctx.height(),
                AV_PIX_FMT_YUV420P,
                SWS_BILINEAR,
                null,
                null,
                (DoublePointer) null
        );

        if (sws_ctx == null) {
            System.out.println("Can not use sws");
            throw new IllegalStateException();
        }

        av_image_fill_arrays(pFrameRGB.data(), pFrameRGB.linesize(),
                buffer, AV_PIX_FMT_YUV420P, codec_ctx.width(), codec_ctx.height(), 1);

        final int FRAMES_COUNT = 25;
        int ret1 = -1, ret2 = -1, fi = -1;



        for (int stage = 0; stage < 3; stage++) {
            long q = fmt_ctx.duration();
            double part = (fmt_ctx.duration() / (double) AV_TIME_BASE) / 100;
            long startT = (long) (part * 15), midT = (long) (part * 50), lastT = (long) (part * 85);
            String stageStr;
            switch (stage) {
                case 0:
                    stageStr = "start";
                    seek_time(fmt_ctx, v_stream_idx, startT);
                    break;
                case 1:
                    stageStr = "mid";
                    seek_time(fmt_ctx, v_stream_idx, midT);
                    break;
                case 2:
                    stageStr = "last";
                    seek_time(fmt_ctx, v_stream_idx, lastT);
                    break;
                default:
                    stageStr = "";

            }
            i = 0;
            while (av_read_frame(fmt_ctx, pkt) >= 0) {
                if (pkt.stream_index() == v_stream_idx) {
                    ret1 = avcodec_send_packet(codec_ctx, pkt);
                    ret2 = avcodec_receive_frame(codec_ctx, frm);
                    System.out.printf("ret1 %d ret2 %d\n", ret1, ret2);
                    // avcodec_decode_video2(codec_ctx, frm, fi, pkt);
                }
                // if not check ret2, error occur [swscaler @ 0x1cb3c40] bad src image pointers
                // ret2 same as fi
                // if (fi && ++i <= 5) {
                if (ret2 >= 0 && ++i <= FRAMES_COUNT) {
                    sws_scale(
                            sws_ctx,
                            frm.data(),
                            frm.linesize(),
                            0,
                            codec_ctx.height(),
                            pFrameRGB.data(),
                            pFrameRGB.linesize()
                    );

                    save_frame(pFrameRGB, codec_ctx.width(), codec_ctx.height(), i, stageStr);
                    /*
                    Video encoding
                    LOL!!!
                     */
                    int retV, got_output;
//                    ffmpeg_encoder_set_frame_yuv_from_rgb(rgb);
//                    ffmpeg_encoder_scale(rgb);

                    av_init_packet(videoPkt);
                    IntPointer videoBuffer = new IntPointer();
                    ret = avcodec_encode_video2(vcCtx, pkt, pFrameRGB, videoBuffer);
                    if (ret < 0) {
                        System.out.println("Error encoding frame");
                        throw new IllegalStateException();
                    }
                    if (!videoBuffer.isNull()) {
                        pFile.write(videoPkt.data().asByteBuffer().array());
                        pFile.flush();
                        av_packet_unref(videoPkt);
                    }

                    save_frame(frm, codec_ctx.width(), codec_ctx.height(), i, stageStr);
                }
                av_packet_unref(pkt);
                if (i >= FRAMES_COUNT) {
                    break;
                }
            }
        }


        av_frame_free(frm);

        avcodec_close(codec_ctx);
        avcodec_free_context(codec_ctx);

        avformat_close_input(fmt_ctx);
        System.out.println("Shutdown");
        System.exit(0);
    }
}
