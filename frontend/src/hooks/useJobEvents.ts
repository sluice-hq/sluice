import { useEffect } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { BASE_URL } from '@/api/client';
import { Job } from '@/api/types';

export function useJobEvents(jobId: string | undefined) {
  const queryClient = useQueryClient();

  useEffect(() => {
    if (!jobId) return;

    const eventSource = new EventSource(`${BASE_URL}/jobs/${jobId}/events`);

    eventSource.onmessage = (event) => {
      try {
        const parsedData = JSON.parse(event.data);
        const { jobId: eventJobId, status, timestamp } = parsedData;

        // Update the specific job in the cache
        queryClient.setQueryData(['job', eventJobId], (oldData: Job | undefined) => {
          if (!oldData) return oldData;
          return {
            ...oldData,
            status,
            updatedAt: timestamp || new Date().toISOString(),
          };
        });

        // Invalidate the jobs list and dashboard to ensure everything is in sync
        queryClient.invalidateQueries({ queryKey: ['jobs'] });
        queryClient.invalidateQueries({ queryKey: ['dashboard'] });
        queryClient.invalidateQueries({ queryKey: ['assets'] });

        // Close the connection if the job is in a terminal state
        if (status === 'COMPLETED' || status === 'FAILED') {
          eventSource.close();
        }
      } catch (e) {
        console.error('Failed to parse SSE message', e);
      }
    };

    eventSource.onerror = (error) => {
      console.error('SSE Error:', error);
      eventSource.close();
    };

    return () => {
      eventSource.close();
    };
  }, [jobId, queryClient]);
}
