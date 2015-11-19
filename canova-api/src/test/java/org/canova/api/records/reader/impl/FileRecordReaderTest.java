package org.canova.api.records.reader.impl;

import org.canova.api.split.FileSplit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Created by nyghtowl on 11/14/15.
 */
public class FileRecordReaderTest {
    protected File file1, file2, file3, file4, newPath;
    protected static String localPath = System.getProperty("java.io.tmpdir") + File.separator;
    protected static String testPath = localPath + "test-folder" + File.separator;

    // TODO fix for TravisCI - tests work

    @Before
    public void doBefore() throws IOException {
        newPath = new File(testPath);

        newPath.mkdir();

        file1 = File.createTempFile("myfile_1", ".jpg", newPath);
        file2 = File.createTempFile("myfile_2", ".txt", newPath);
        file3 = File.createTempFile("myfile_3", ".jpg", newPath);
        file4 = File.createTempFile("treehouse_4", ".jpg", newPath);
    }

    @Test
    public void testNextAndReset() throws Exception {
        FileRecordReader reader = new FileRecordReader();
        reader.initialize(new FileSplit(new File(testPath)));

        assertTrue(reader.hasNext());

        while(reader.hasNext()){
            reader.next();
        }
        assertFalse(reader.hasNext());
        reader.reset(); // reset shouldn't work on record readers
        assertFalse(reader.hasNext());

    }

    @After
    public void doAfter(){
        file1.delete();
        file2.delete();
        file3.delete();
        file4.delete();
        newPath.delete();
    }

}
