import { getJson, patchJson } from '../../lib/http';
import type {
  Category,
  CategoryList,
  PeriodKind,
  UpdateCategoryPlanRequest,
} from '../../types/api';

export function fetchCategories(period: PeriodKind): Promise<CategoryList> {
  return getJson<CategoryList>(`/categories?period=${period}`);
}

export function updateCategoryPlan(
  id: string,
  changes: UpdateCategoryPlanRequest,
): Promise<Category> {
  return patchJson<Category>(`/categories/${id}`, changes);
}
