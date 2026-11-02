import { useQuery } from '@tanstack/react-query';
import { FeaturesApi } from '../api/endpoints';
import type { Features } from '../api/types';

const FALLBACK: Features = { evidenceOcr: false };

export function useFeatures(): Features {
  const { data } = useQuery({
    queryKey: ['features'],
    queryFn: FeaturesApi.get,
    staleTime: 5 * 60_000,
  });
  return data ?? FALLBACK;
}
