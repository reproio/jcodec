package org.jcodec.api.android;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.jcodec.codecs.h264.H264Encoder;
import org.jcodec.codecs.h264.H264Utils;
import org.jcodec.common.NIOUtils;
import org.jcodec.common.SeekableByteChannel;
import org.jcodec.common.model.ColorSpace;
import org.jcodec.common.model.Picture;
import org.jcodec.common.model.Size;
import org.jcodec.containers.mp4.Brand;
import org.jcodec.containers.mp4.MP4Packet;
import org.jcodec.containers.mp4.TrackType;
import org.jcodec.containers.mp4.muxer.FramesMP4MuxerTrack;
import org.jcodec.containers.mp4.muxer.MP4Muxer;
import org.jcodec.scale.RgbToYuv420p;
import org.jcodec.scale.BitmapUtil;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

/**
 * This class is part of JCodec ( www.jcodec.org ) This software is distributed
 * under FreeBSD License
 *
 * @author The JCodec project
 *
 */
public class SequenceMuxer {
    private SeekableByteChannel ch;
    private FramesMP4MuxerTrack outTrack;
    private int frameNo;
    private MP4Muxer muxer;
    private Size size;

    private H264Encoder encoder = new H264Encoder();
    RgbToYuv420p transform = new RgbToYuv420p(0, 0);

    Picture yuv = null;
    ByteBuffer _out = null;

    public SequenceMuxer(File out) throws IOException {
        this.ch = NIOUtils.writableFileChannel(out);

        // Muxer that will store the encoded frames
        muxer = new MP4Muxer(ch, Brand.MP4);

        // Add video track to muxer
        outTrack = muxer.addTrack(TrackType.VIDEO, 25);
    }

    public void encodeImage(File png) throws IOException {
        if (size == null) {
            Bitmap read = BitmapFactory.decodeFile(png.getAbsolutePath());
            size = new Size(read.getWidth(), read.getHeight());
        }
        // Add packet to video track
        outTrack.addFrame(new MP4Packet(NIOUtils.fetchFrom(png), frameNo, 25, 1, frameNo, true, null, frameNo, 0));

        frameNo++;
    }

    public void encodeImage(File png, int fps) throws IOException {
        if (size == null) {
            Bitmap read = BitmapFactory.decodeFile(png.getAbsolutePath());
            size = new Size(read.getWidth(), read.getHeight());
        }
        // Add packet to video track
        outTrack.addFrame(new MP4Packet(NIOUtils.fetchFrom(png), frameNo, fps, 1, frameNo, true, null, frameNo, 0));
        frameNo++;
    }

    public static void fromBitmap(Bitmap src, Picture dst) {
        int[] dstData = dst.getPlaneData(0);
        int[] packed = new int[src.getWidth() * src.getHeight()];

        src.getPixels(packed, 0, src.getWidth(), 0, 0, src.getWidth(), src.getHeight());

        for (int i = 0, srcOff = 0, dstOff = 0; i < src.getHeight(); i++) {
            for (int j = 0; j < src.getWidth(); j++, srcOff++, dstOff += 3) {
                int rgb = packed[srcOff];
                dstData[dstOff]     = (rgb >> 16) & 0xff;
                dstData[dstOff + 1] = (rgb >> 8) & 0xff;
                dstData[dstOff + 2] = rgb & 0xff;
            }
        }
    }

    public void encodeImage(Bitmap bmp) throws IOException {
        if (size == null) {
            size = new Size(bmp.getWidth(), bmp.getHeight());
        }

        ByteBuffer buffer = ByteBuffer.allocate(bmp.getByteCount());
        // Add packet to video track
        outTrack.addFrame(new MP4Packet(buffer, frameNo, 25, 1, frameNo, true, null, frameNo, 0));
        frameNo++;
    }

    public void encodeImage2(Bitmap bmp, int fps) throws IOException {
        Log.i("SequenceMuxer", "1");
        if (size == null) {
            size = new Size(bmp.getWidth(), bmp.getHeight());
        }
        Log.i("SequenceMuxer", "2");

        Picture rgb = BitmapUtil.fromBitmap(bmp);
        if (yuv == null) {
            yuv = Picture.create(rgb.getWidth(), rgb.getHeight(), ColorSpace.YUV420);
        }

        Log.i("SequenceMuxer", "3");
        transform.transform(rgb, yuv);

        Log.i("SequenceMuxer", "4");
        if (_out == null) {
            _out = ByteBuffer.allocate(yuv.getWidth() * yuv.getHeight() * 3);
        }

        Log.i("SequenceMuxer", "5");
        Log.i("SequenceMuxer", String.format("bmp: %d/%d rgb: %d/%d yuv:%d/%d",
                                             bmp.getWidth(), bmp.getHeight(),
                                             rgb.getWidth(), rgb.getHeight(),
                                             yuv.getWidth(), yuv.getHeight()
        ));

        ByteBuffer result = encoder.encodeFrame(yuv, _out);
//        outTrack.addFrame(new MP4Packet(buffer, frameNo * 2, 1, 2, frameNo, true, null, frameNo * 2, 0));

        Log.i("SequenceMuxer", "6");
        outTrack.addFrame(new MP4Packet(result, frameNo, fps, 1, frameNo, true, null, frameNo, 0));
        frameNo++;

        Log.i("SequenceMuxer", "7");
        // free
        bmp.recycle();
        _out.clear();
    }


    public void encodeImageRGB(Bitmap bmp, int fps) throws IOException {
        Log.i("SequenceMuxer", "1");
        if (size == null) {
            size = new Size(bmp.getWidth(), bmp.getHeight());
        }
        Log.i("SequenceMuxer", "2");

        Picture rgb = BitmapUtil.fromBitmap(bmp);

        if (_out == null) {
            _out = ByteBuffer.allocate(rgb.getWidth() * rgb.getHeight() * 6);
        }

        Log.i("SequenceMuxer", "3");

        ByteBuffer result = encoder.encodeFrame(rgb, _out);

        Log.i("SequenceMuxer", "4");

        outTrack.addFrame(new MP4Packet(result, frameNo, fps, 1, frameNo, true, null, frameNo, 0));
        frameNo++;

        Log.i("SequenceMuxer", "5");
        // free
        bmp.recycle();
        _out.clear();
    }

    public void finish() throws IOException {
        // Push saved SPS/PPS to a special storage in MP4
        outTrack.addSampleEntry(MP4Muxer.videoSampleEntry("png ", size, "JCodec"));

        // Write MP4 header and finalize recording
        muxer.writeHeader();
        NIOUtils.closeQuietly(ch);
    }

    public void setSize(Size size) {
        this.size = size;
    }
}
