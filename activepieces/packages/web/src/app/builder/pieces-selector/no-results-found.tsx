import { t } from 'i18next';
import { SearchX } from 'lucide-react';

const NoResultsFound = () => {
  return (
    <div className="flex flex-col gap-2 items-center justify-center h-full ">
      <SearchX className="w-14 h-14" />
      <div className="text-sm ">{t('No pieces found')}</div>
      <div className="text-sm ">{t('Try adjusting your search')}</div>
    </div>
  );
};

export { NoResultsFound };
