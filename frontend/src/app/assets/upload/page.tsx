'use client';

import { useState, useCallback } from 'react';
import { useRouter } from 'next/navigation';
import { requestUploadUrl, completeUpload } from '@/api/assets';
import { Button } from '@/components/ui/button';
import { Upload, X, FileVideo, CheckCircle2, ArrowLeft, Loader2, XCircle } from 'lucide-react';
import { cn } from '@/lib/utils';
import Link from 'next/link';
import { useQuery } from '@tanstack/react-query';
import { getPublishedPipelines } from '@/api/pipelines';
import { startRun } from '@/api/runs';

type UploadStep = 'IDLE' | 'REQUESTING' | 'UPLOADING' | 'VERIFYING' | 'COMPLETED' | 'ERROR';

export default function UploadPage() {
  const router = useRouter();
  const [file, setFile] = useState<File | null>(null);
  const [step, setStep] = useState<UploadStep>('IDLE');
  const [error, setError] = useState<string | null>(null);
  const [assetId, setAssetId] = useState<string | null>(null);
  const [pipelineSlug, setPipelineSlug] = useState('');
  const { data: pipelines = [], isLoading: pipelinesLoading, error: pipelinesError } = useQuery({
    queryKey: ['pipelines', 'published'],
    queryFn: getPublishedPipelines,
  });

  const onDragOver = useCallback((e: React.DragEvent) => {
    e.preventDefault();
  }, []);

  const onDrop = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    if (e.dataTransfer.files && e.dataTransfer.files.length > 0) {
      setFile(e.dataTransfer.files[0]);
    }
  }, []);

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files.length > 0) {
      setFile(e.target.files[0]);
    }
  };

  const handleUpload = async () => {
    if (!file || !pipelineSlug) return;
    
    try {
      setStep('REQUESTING');
      setError(null);
      
      // Step 1: Request SAS URL
      const { assetId: newAssetId, uploadUrl } = await requestUploadUrl({
        filename: file.name,
        contentType: file.type || 'application/octet-stream',
        size: file.size,
      });

      setAssetId(newAssetId);
      setStep('UPLOADING');

      // Step 2: Upload directly to Azure Blob Storage
      const uploadResponse = await fetch(uploadUrl, {
        method: 'PUT',
        body: file,
        headers: {
          'x-ms-blob-type': 'BlockBlob',
          'Content-Type': file.type || 'application/octet-stream'
        }
      });

      if (!uploadResponse.ok) {
        throw new Error('Failed to upload file to Azure Blob Storage');
      }

      setStep('VERIFYING');
      
      // Step 3: Complete upload in Sluice
      await completeUpload(newAssetId);
      await startRun(pipelineSlug, newAssetId);
      
      setStep('COMPLETED');
    } catch (err: unknown) {
      setStep('ERROR');
      setError(err instanceof Error ? err.message : 'An unexpected error occurred during upload.');
    }
  };

  const isUploading = ['REQUESTING', 'UPLOADING', 'VERIFYING'].includes(step);

  return (
    <div className="max-w-3xl mx-auto space-y-6">
      <Link href="/assets">
        <Button variant="ghost" size="sm" className="-ml-4 text-muted-foreground">
          <ArrowLeft className="w-4 h-4 mr-2" />
          Back to Assets
        </Button>
      </Link>

      <div>
        <h2 className="text-2xl font-bold tracking-tight">Test a pipeline</h2>
        <p className="text-muted-foreground mt-1">Manually upload media through Azure SAS to verify a published pipeline before integrating the API.</p>
      </div>

      <div className="bg-card border border-border rounded-xl p-8 shadow-sm">
        {step === 'COMPLETED' ? (
          <div className="flex flex-col items-center justify-center py-12 text-center">
            <div className="w-16 h-16 bg-status-success/20 rounded-full flex items-center justify-center mb-6">
              <CheckCircle2 className="w-8 h-8 text-status-success" />
            </div>
            <h3 className="text-2xl font-semibold text-white">Upload Successful</h3>
            <p className="text-muted-foreground mt-2 max-w-md">
              The asset has been securely uploaded and a new processing job has been queued.
            </p>
            <div className="mt-8 flex gap-4">
              <Button onClick={() => router.push(`/assets/${assetId}`)}>View Asset Details</Button>
              <Button variant="outline" onClick={() => {
                setFile(null);
                setStep('IDLE');
                setAssetId(null);
              }}>Upload Another</Button>
            </div>
          </div>
        ) : (
          <div className="space-y-8">
            {!file ? (
              <div 
                className="border-2 border-dashed border-border rounded-lg p-12 text-center hover:bg-white/[0.02] transition-colors cursor-pointer"
                onDragOver={onDragOver}
                onDrop={onDrop}
                onClick={() => document.getElementById('file-upload')?.click()}
              >
                <div className="w-16 h-16 bg-primary/10 rounded-full flex items-center justify-center mx-auto mb-4">
                  <Upload className="w-8 h-8 text-primary" />
                </div>
                <h3 className="text-lg font-medium text-white">Click or drag a file to upload</h3>
                <p className="text-sm text-muted-foreground mt-1">JPEG, PNG, GIF, PDF, or MP4 files up to 50 MB.</p>
                <input 
                  type="file" 
                  id="file-upload" 
                  className="hidden" 
                  accept="image/jpeg,image/png,image/gif,application/pdf,video/mp4"
                  onChange={handleFileChange}
                />
              </div>
            ) : (
              <div className="space-y-6">
                <div className="bg-primary/5 border border-primary/20 rounded-lg p-4 flex items-center justify-between">
                  <div className="flex items-center gap-4">
                    <div className="bg-primary/20 p-2 rounded">
                      <FileVideo className="w-6 h-6 text-primary" />
                    </div>
                    <div>
                      <p className="font-medium text-white">{file.name}</p>
                      <p className="text-sm text-muted-foreground">{(file.size / 1024 / 1024).toFixed(2)} MB</p>
                    </div>
                  </div>
                  {!isUploading && (
                    <Button variant="ghost" size="icon" onClick={() => setFile(null)} className="text-muted-foreground hover:text-status-error">
                      <X className="w-5 h-5" />
                    </Button>
                  )}
                </div>

                {isUploading && (
                  <div className="space-y-4">
                    <div className="flex justify-between text-sm font-medium">
                      <span className={step === 'REQUESTING' ? 'text-primary' : 'text-muted-foreground'}>1. Requesting SAS URL...</span>
                      <span className={step === 'UPLOADING' ? 'text-primary' : 'text-muted-foreground'}>2. Uploading to Azure...</span>
                      <span className={step === 'VERIFYING' ? 'text-primary' : 'text-muted-foreground'}>3. Verifying & Queuing...</span>
                    </div>
                    <div className="w-full bg-background h-2 rounded-full overflow-hidden">
                      <div 
                        className={cn("h-full bg-primary transition-all duration-500", 
                          step === 'REQUESTING' ? 'w-1/3' : 
                          step === 'UPLOADING' ? 'w-2/3 animate-pulse' : 
                          step === 'VERIFYING' ? 'w-full animate-pulse' : 'w-0'
                        )}
                      />
                    </div>
                  </div>
                )}

                {error && (
                  <div className="bg-status-error/10 border border-status-error/30 text-status-error p-4 rounded-md text-sm flex items-start gap-3">
                    <XCircle className="w-5 h-5 flex-shrink-0 mt-0.5" />
                    <div>
                      <h4 className="font-semibold">Upload Failed</h4>
                      <p>{error}</p>
                    </div>
                  </div>
                )}

                <div className="space-y-2">
                  <label htmlFor="pipeline" className="text-sm font-medium">Processing pipeline</label>
                  <select
                    id="pipeline"
                    value={pipelineSlug}
                    onChange={(event) => setPipelineSlug(event.target.value)}
                    disabled={isUploading || pipelinesLoading}
                    className="w-full h-10 rounded-md border border-border bg-background px-3 text-sm disabled:opacity-50"
                    required
                  >
                    <option value="">{pipelinesLoading ? 'Loading pipelines…' : 'Select a published pipeline'}</option>
                    {pipelines.map((pipeline) => (
                      <option key={pipeline.id} value={pipeline.slug}>
                        {pipeline.name} (v{pipeline.versionNumber}, {pipeline.expectedInputMimeType})
                      </option>
                    ))}
                  </select>
                  {pipelinesError && (
                    <p className="text-xs text-status-error">Could not load pipelines. Check API Connection in Settings.</p>
                  )}
                  {!pipelinesLoading && !pipelinesError && pipelines.length === 0 && (
                    <p className="text-xs text-muted-foreground">This project has no published pipelines yet.</p>
                  )}
                </div>

                <div className="flex justify-end gap-3 pt-4 border-t border-border">
                  <Button variant="outline" onClick={() => router.back()} disabled={isUploading}>
                    Cancel
                  </Button>
                  <Button onClick={handleUpload} disabled={isUploading || !pipelineSlug}>
                    {isUploading ? (
                      <>
                        <Loader2 className="w-4 h-4 mr-2 animate-spin" />
                        Processing...
                      </>
                    ) : (
                      <>
                        <Upload className="w-4 h-4 mr-2" />
                        Start Upload
                      </>
                    )}
                  </Button>
                </div>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
