'use client';

import { useState, useCallback } from 'react';
import { useRouter } from 'next/navigation';
import { requestUploadUrl, completeUpload } from '@/api/assets';
import { Button } from '@/components/ui/button';
import { Upload, X, FileVideo, CheckCircle2, ArrowLeft, Loader2, XCircle } from 'lucide-react';
import { cn } from '@/lib/utils';
import Link from 'next/link';

type UploadStep = 'IDLE' | 'REQUESTING' | 'UPLOADING' | 'VERIFYING' | 'COMPLETED' | 'ERROR';

export default function UploadPage() {
  const router = useRouter();
  const [file, setFile] = useState<File | null>(null);
  const [step, setStep] = useState<UploadStep>('IDLE');
  const [error, setError] = useState<string | null>(null);
  const [assetId, setAssetId] = useState<string | null>(null);

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
    if (!file) return;
    
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
      
      setStep('COMPLETED');
    } catch (err: any) {
      setStep('ERROR');
      setError(err.message || 'An unexpected error occurred during upload.');
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
        <h2 className="text-2xl font-bold tracking-tight">Upload Asset</h2>
        <p className="text-muted-foreground mt-1">Upload a new media file to the platform directly via Azure SAS.</p>
      </div>

      <div className="bg-white border rounded-xl p-8 shadow-sm">
        {step === 'COMPLETED' ? (
          <div className="flex flex-col items-center justify-center py-12 text-center">
            <div className="w-16 h-16 bg-green-100 rounded-full flex items-center justify-center mb-6">
              <CheckCircle2 className="w-8 h-8 text-green-600" />
            </div>
            <h3 className="text-2xl font-semibold text-gray-900">Upload Successful</h3>
            <p className="text-gray-500 mt-2 max-w-md">
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
                className="border-2 border-dashed border-gray-300 rounded-lg p-12 text-center hover:bg-gray-50 transition-colors cursor-pointer"
                onDragOver={onDragOver}
                onDrop={onDrop}
                onClick={() => document.getElementById('file-upload')?.click()}
              >
                <div className="w-16 h-16 bg-blue-50 rounded-full flex items-center justify-center mx-auto mb-4">
                  <Upload className="w-8 h-8 text-blue-500" />
                </div>
                <h3 className="text-lg font-medium text-gray-900">Click or drag a file to upload</h3>
                <p className="text-sm text-gray-500 mt-1">Video, audio, or image files up to 5GB.</p>
                <input 
                  type="file" 
                  id="file-upload" 
                  className="hidden" 
                  onChange={handleFileChange}
                />
              </div>
            ) : (
              <div className="space-y-6">
                <div className="bg-blue-50 border border-blue-100 rounded-lg p-4 flex items-center justify-between">
                  <div className="flex items-center gap-4">
                    <div className="bg-blue-100 p-2 rounded">
                      <FileVideo className="w-6 h-6 text-blue-600" />
                    </div>
                    <div>
                      <p className="font-medium text-gray-900">{file.name}</p>
                      <p className="text-sm text-gray-500">{(file.size / 1024 / 1024).toFixed(2)} MB</p>
                    </div>
                  </div>
                  {!isUploading && (
                    <Button variant="ghost" size="icon" onClick={() => setFile(null)} className="text-gray-500 hover:text-red-500">
                      <X className="w-5 h-5" />
                    </Button>
                  )}
                </div>

                {isUploading && (
                  <div className="space-y-4">
                    <div className="flex justify-between text-sm font-medium">
                      <span className={step === 'REQUESTING' ? 'text-blue-600' : 'text-gray-500'}>1. Requesting SAS URL...</span>
                      <span className={step === 'UPLOADING' ? 'text-blue-600' : 'text-gray-500'}>2. Uploading to Azure...</span>
                      <span className={step === 'VERIFYING' ? 'text-blue-600' : 'text-gray-500'}>3. Verifying & Queuing...</span>
                    </div>
                    <div className="w-full bg-gray-100 h-2 rounded-full overflow-hidden">
                      <div 
                        className={cn("h-full bg-blue-600 transition-all duration-500", 
                          step === 'REQUESTING' ? 'w-1/3' : 
                          step === 'UPLOADING' ? 'w-2/3 animate-pulse' : 
                          step === 'VERIFYING' ? 'w-full animate-pulse' : 'w-0'
                        )}
                      />
                    </div>
                  </div>
                )}

                {error && (
                  <div className="bg-red-50 border border-red-200 text-red-700 p-4 rounded-md text-sm flex items-start gap-3">
                    <XCircle className="w-5 h-5 flex-shrink-0 mt-0.5" />
                    <div>
                      <h4 className="font-semibold">Upload Failed</h4>
                      <p>{error}</p>
                    </div>
                  </div>
                )}

                <div className="flex justify-end gap-3 pt-4 border-t">
                  <Button variant="outline" onClick={() => router.back()} disabled={isUploading}>
                    Cancel
                  </Button>
                  <Button onClick={handleUpload} disabled={isUploading}>
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
