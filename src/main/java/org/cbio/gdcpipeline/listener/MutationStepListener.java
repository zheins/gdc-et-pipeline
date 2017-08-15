package org.cbio.gdcpipeline.listener;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.tomcat.util.buf.StringUtils;
import org.cbio.gdcpipeline.model.rest.response.GdcFileMetadata;
import org.cbio.gdcpipeline.util.CommonDataUtil;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.item.ItemStreamException;
import org.springframework.beans.factory.annotation.Value;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Dixit Patel
 **/
public class MutationStepListener implements StepExecutionListener {
    private static Log LOG = LogFactory.getLog(MutationStepListener.class);

    @Value("${mutation.data.file.prefix}")
    private String MUTATION_DATA_FILE_PREFIX;

    @Value("${mutation.default.merged.maf.file}")
    private String MUTATION_DEFAULT_MERGED_MAF_FILE;

    @Value("#{jobParameters[sourceDirectory]}")
    private String sourceDir;

    @Value("#{jobParameters[outputDirectory]}")
    private String outputDir;

    @Value("#{jobExecutionContext[gdcFileMetadatas]}")
    private List<GdcFileMetadata> gdcFileMetadatas;

    @Value("#{jobParameters[separate_maf]}")
    private String separate_maf;

    @Override
    public void beforeStep(StepExecution stepExecution) {
        boolean isStarted = false;
        if (stepExecution.getJobExecution().getExecutionContext().containsKey("isStartedMaf")) {
            isStarted = true;
        }
        if (!isStarted) {
            List<File> maf_files = getMutationFileList();
            if (!separate_maf.isEmpty()) {
                if (separate_maf.equalsIgnoreCase("true")) {
                    //used by metadata step
                    List<String> maf_filenames = new ArrayList<>();
                    for(File file : maf_files){
                        maf_filenames.add(MUTATION_DATA_FILE_PREFIX+file.getName());
                    }
                    stepExecution.getJobExecution().getExecutionContext().put("mutation_data_filenames", maf_filenames);

                    //read individually
                    List<File> mafToProcess = new ArrayList<>();
                    mafToProcess.add(maf_files.remove(0));
                    stepExecution.getJobExecution().getExecutionContext().put("maf_files", maf_files);
                    stepExecution.getExecutionContext().put("mafToProcess", mafToProcess);
                    stepExecution.getJobExecution().getExecutionContext().put("isStartedMaf", true);
                } else {
                    //Read all MAF's together
                    List<String> mutation_data_filenames = new ArrayList<>();
                    mutation_data_filenames.add(MUTATION_DEFAULT_MERGED_MAF_FILE);
                    stepExecution.getJobExecution().getExecutionContext().put("mutation_data_filenames",mutation_data_filenames );
                    stepExecution.getExecutionContext().put("mafToProcess", maf_files);
                }
            }
        } else {
            if (!separate_maf.isEmpty()) {
                if (separate_maf.equalsIgnoreCase("true")) {
                    List<File> maf_files = (List<File>) stepExecution.getJobExecution().getExecutionContext().get("maf_files");
                    List<File> mafToProcess = new ArrayList<>();
                    mafToProcess.add(maf_files.remove(0));
                    stepExecution.getExecutionContext().put("mafToProcess", mafToProcess);
                }
            }
        }
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        if (!separate_maf.isEmpty()) {
            if (separate_maf.equalsIgnoreCase("true")) {
                List<String> mafList = (List<String>) stepExecution.getJobExecution().getExecutionContext().get("maf_files");
                if (!mafList.isEmpty()) {
                    return new ExitStatus("CONTINUE");
                }
            }
        }
        return ExitStatus.COMPLETED;
    }

    public List<File> getMutationFileList() {
        List<File> fileList = CommonDataUtil.getFileList(gdcFileMetadatas, CommonDataUtil.GDC_TYPE.MUTATION, sourceDir);
        List<File> mutationFileList = new ArrayList<>();
        for (File file : fileList) {
            File maf =new File(sourceDir,file.getName().replace(".gz", ""));
            mutationFileList.add(maf);
        }
        return mutationFileList;
    }
}
