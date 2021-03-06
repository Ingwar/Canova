package org.canova.image.recordreader;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.canova.api.berkeley.Pair;
import org.canova.api.io.data.DoubleWritable;
import org.canova.api.io.data.Text;
import org.canova.api.split.FileSplit;
import org.canova.api.split.InputSplit;
import org.canova.api.writable.Writable;
import org.canova.common.RecordConverter;
import org.canova.image.loader.BaseImageLoader;
import org.canova.image.loader.ImageLoader;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URI;
import java.util.*;

/**
 * Record reader to handle ImageNet dataset
 **
 * Built to avoid changing api at this time. Api should change to track labels that are only referenced by id in filename
 * Creates a hashmap for label name to id and references that with filename to generate matching lables.
 */
public class ImageNetRecordReader extends BaseImageRecordReader {

    protected static Logger log = LoggerFactory.getLogger(ImageNetRecordReader.class);
    protected Map<String,String> labelFileIdMap = new LinkedHashMap<>();
    protected String labelPath;
    protected String fileNameMapPath = null; // use when the WNID is not in the filename (e.g. val labels)
    protected boolean eval = false; // use to load label ids for validation data set

    public ImageNetRecordReader(int width, int height, int channels, String labelPath) {
        this(width, height, channels, labelPath, null, false, null, 0);
    }

    public ImageNetRecordReader(int width, int height, int channels, String labelPath, boolean appendLabel) {
        this(width, height, channels, labelPath, null, appendLabel, null, 0);
    }

    public ImageNetRecordReader(int width, int height, int channels, String labelPath, boolean appendLabel, String pattern) {
        this(width, height, channels, labelPath, null, appendLabel, pattern, 0);
    }

    public ImageNetRecordReader(int width, int height, int channels, String labelPath, boolean appendLabel, String pattern, int patternPosition) {
        this(width, height, channels, labelPath, null, appendLabel, pattern, patternPosition);
    }

    public ImageNetRecordReader(int width, int height, int channels, String labelPath, String fileNameMapPath, boolean appendLabel) {
        this(width, height, channels, labelPath, fileNameMapPath, appendLabel, null, 0);
        this.eval = true;
    }

    public ImageNetRecordReader(int width, int height, int channels, String labelPath, String fileNameMapPath, boolean appendLabel, String pattern, int patternPosition) {
        imageLoader = new ImageLoader(width, height, channels);
        this.labelPath = labelPath;
        this.appendLabel = appendLabel;
        this.fileNameMapPath = fileNameMapPath;
        this.pattern = pattern;
        this.patternPosition = patternPosition;
        this.eval = true;
    }

    private Map<String, String> defineLabels(String path) throws IOException {
        Map<String,String> tmpMap = new LinkedHashMap<>();
        BufferedReader br = new BufferedReader(new FileReader(path));
        String line;

        while ((line = br.readLine()) != null) {
            String row[] = line.split(",");
            tmpMap.put(row[0], row[1]);
            labels.add(row[1]);
        }
        return tmpMap;
    }

    private void imgNetLabelSetup() throws IOException {
        // creates hashmap with WNID (synset id) as key and first descriptive word in list as the string name
        if (labelPath != null && labelFileIdMap.isEmpty()) {
            labelFileIdMap = defineLabels(labelPath);
            labels = new ArrayList<>(labelFileIdMap.values());
        }
        // creates hasmap with filename as key and WNID(synset id) as value
        if (fileNameMapPath != null && fileNameMap.isEmpty()) {
            fileNameMap = defineLabels(fileNameMapPath);
        }
    }

    @Override
    public void initialize(InputSplit split) throws IOException {
        inputSplit = split;
        imgNetLabelSetup();

        if(split instanceof FileSplit) {
            URI[] locations = split.locations();
            if(locations != null && locations.length >= 1) {
                if(locations.length > 1) {
                    List<File> allFiles = new ArrayList<>();
                    for(URI location : locations) {
                        File iter = new File(location);
                        if(!iter.isDirectory() && containsFormat(iter.getAbsolutePath()))
                            allFiles.add(iter);
                    }
                    iter =  allFiles.listIterator();
                }
                else {
                    File curr = new File(locations[0]);
                    if(!curr.exists())
                        throw new IllegalArgumentException("Path " + curr.getAbsolutePath() + " does not exist!");
                    if(curr.isDirectory())
                        iter = FileUtils.iterateFiles(curr, null, true);
                    else
                        iter =  Collections.singletonList(curr).listIterator();
                }
            }
        } else {
            throw new UnsupportedClassVersionError("Split needs to be an instance of FileSplit for this record reader.");
        }

    }

    @Override
    public Collection<Writable> next() {
        if(iter != null) {
            File image = iter.next();

            if(image.isDirectory())
                return next();

            try {
                return load(ImageIO.read(image), image.getName());
            } catch (Exception e) {
                e.printStackTrace();
            }

            Collection<Writable> ret = new ArrayList<>();
            if(iter.hasNext()) {
                return ret;
            }
            else {
                if(iter.hasNext()) {
                    try {
                        ret.add(new Text(FileUtils.readFileToString((File) iter.next())));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
            return ret;
        }
        else if(record != null) {
            hitImage = true;
            return record;
        }
        throw new IllegalStateException("No more elements");
    }

    private Collection<Writable> load(BufferedImage image, String filename){
        int labelId = -1;
        BufferedImage bimg = imageLoader.centerCropIfNeeded(image);
        INDArray row = imageLoader.asRowVector(bimg);
        Collection<Writable> ret = RecordConverter.toRecord(row);
        if(appendLabel && fileNameMapPath == null) {
            String WNID = FilenameUtils.getBaseName(filename).split(pattern)[patternPosition];
            labelId = labels.indexOf(labelFileIdMap.get(WNID));
        } else if (eval) {
            String fileName = FilenameUtils.getName(filename); // currently expects file extension
            labelId = labels.indexOf(labelFileIdMap.get(fileNameMap.get(fileName)));
        }
        if (labelId >= 0)
            ret.add(new DoubleWritable(labelId));
        else
            throw new IllegalStateException("Illegal label " + labelId);
        return ret;
    }

    @Override
    public Collection<Writable> record(URI uri, DataInputStream dataInputStream ) throws IOException {
        BufferedImage bimg = ImageIO.read(dataInputStream);
        imgNetLabelSetup();
        return load(bimg,FilenameUtils.getName(uri.getPath()));
    }
}
